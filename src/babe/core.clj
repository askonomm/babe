(ns babe.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.shell :refer [sh]]
            [selmer.parser :as selmer]
            [selmer.util :as selmer.util]
            [babe.utils :as utils]
            [org.httpkit.client :as client])
  (:gen-class)
  (:import (java.io File)
           (java.util.zip ZipInputStream ZipEntry)))

(set! *warn-on-reflection* true)

; By default, Selmer escapes all HTML entities.
; We don't want that.
(selmer.util/turn-off-escaping!)

; This here may seem unused, but trust me, it isn't. Since we use
; GraalVM to create a native binary of Babe, we need to provide it
; a list of all packages so that it would know what to load for us
; in the classpath. I use this var from the REPL to get a list of
; those packages.
(def ^:private packages
  (->> (map ns-name (all-ns))
       (remove #(str/starts-with? % "clojure"))
       (map #(str/split (str %) #"\."))
       (keep butlast)
       (map #(str/join "." %))
       distinct
       (map munge)
       (cons "clojure")))

(def ^:private base-project-zip-url
  "https://github.com/askonomm/babe-base-project/archive/refs/heads/master.zip")

(defn- scan-assets
  "Recursively scans a given `base-directory` for any asset files
   like CSS, JS, images, etc. The result of this is used to know
   which files to copy to the /public directory."
  [base-directory]
  (utils/scan base-directory
              (fn [read-dir current-path]
                (and (not (= current-path (str read-dir "/public")))
                     (or (str/ends-with? current-path ".css")
                         (str/ends-with? current-path ".js")
                         (str/ends-with? current-path ".png")
                         (str/ends-with? current-path ".jpg")
                         (str/ends-with? current-path ".jpeg")
                         (str/ends-with? current-path ".gif")
                         (str/ends-with? current-path ".webp")
                         (.isDirectory ^File (io/file current-path)))))))

(defn- scan-content
  "Recursively scans a given `base-directory` for any .md or .html
  files. The result of this is used for generating content files."
  [base-directory]
  (utils/scan base-directory
              (fn [read-dir current-path]
                (and (not (= current-path (str read-dir "/public")))
                     (not (= current-path (str read-dir "/layout.sel")))
                     (or (str/ends-with? current-path ".md")
                         (str/ends-with? current-path ".sel")
                         (.isDirectory ^File (io/file current-path)))))))

(defn- scan-watchlist
  "Recursively scans a given `base-directory` for any and all files,
  except those in the /public directory. The result of this is used
  by the watcher to know when any file was modified, to then trigger
  a re-build."
  [base-directory]
  (utils/scan base-directory
              (fn [read-dir current-path]
                (not (= current-path (str read-dir "/public"))))))

(defn- construct-content-item->md
  "Constructs the data structure for a Markdown file by taking the file
  `contents` and parsing it for YAML metadata and Markdown data."
  [base-directory path contents]
  {:path  (-> path
              (str/replace base-directory "")
              (utils/triml "/")
              (str/replace ".md" ""))
   :meta  (utils/parse-md-metadata contents)
   :entry (utils/parse-md-entry contents)})

(defn- construct-content-item->sel
  "Constructs the data structure for a Selmer file. Unlike a
  Markdown file, we will not be creating a index.html file to
  put the rendered contents into, but rather just remove the .sel
  extension and create a file based on the content file name itself,
  hence the :selmer part, so that the builder will know what to do."
  [base-directory path contents]
  {:path     (-> path
                 (str/replace base-directory "")
                 (utils/triml "/")
                 (str/replace ".sel" ""))
   :selmer   true
   :contents contents})

(defn- construct-content-item
  "Build a content item depending on the extension of the file.
  Currently supports .md for Markdown and .sel for Selmer."
  [base-directory {:keys [path]}]
  (let [contents (slurp path)]
    (cond (str/ends-with? path ".md")
          (construct-content-item->md base-directory path contents)
          (str/ends-with? path ".sel")
          (construct-content-item->sel base-directory path contents))))

(defn- construct-content
  "Iterates over each scanned file and returns a collection of
  constructed content items."
  [base-directory]
  (pmap (fn [file]
          (construct-content-item base-directory file))
        (scan-content base-directory)))

(defn- prepend-folder-to-content-item-path
  "Prepends a given `folder` to the :path of each of the items
  in `content`"
  [folder content]
  (map (fn [content-item]
         (merge content-item
                {:path (str folder "/" (:path content-item))}))
       content))

(defn- construct-templating-data-item
  "Builds a templating data item, which is a collection of content
  items from a specified folder. Optionally sorted and ordered, and
  returned as a key-value map to be consumed a Selmer template."
  [base-directory data-item]
  (let [folder (-> (:folder data-item)
                   (utils/triml "/")
                   (utils/trimr "/"))
        content (->> (str (utils/trimr base-directory "/") "/" folder)
                     (construct-content)
                     (prepend-folder-to-content-item-path folder))]
    {(keyword (:name data-item))
     (cond->> content
              (:sortBy data-item)
              (sort-by #(get-in % [:meta (keyword (:sortBy data-item))]))
              (= "desc" (:order data-item))
              (reverse))}))

(defn- construct-templating-data
  "Builds the templating data from the given configuration and
  data-set defined in the babe.json file, allowing for pretty
  dynamic use."
  [base-directory config]
  {:site (:site config)
   :data (into {} (map #(construct-templating-data-item base-directory %)
                       (:data config)))})

(defn- get-template
  "Attempts to retrieve a given `template` from within the
  `base-directory`. If it cannot find the template, it will
  return an empty string instead."
  [base-directory template]
  (try
    (-> (str (utils/trimr base-directory "/") "/" template ".sel")
        (slurp))
    (catch Exception _
      "")))

(defn- get-config
  "Attempts to retrieve the `babe.json` contents and parse it
  into EDN. If it cannot find the file, will return an empty
  map instead."
  [base-directory]
  (try
    (-> (str (utils/trimr base-directory "/") "/babe.json")
        (slurp)
        (json/read-str :key-fn keyword))
    (catch Exception _
      {})))

(defn- write->sel!
  "Writes a Selmer template into the /public directory based on its
  path and filename of the file.

  For example, a Selmer template located in /about/john.html.sel will
  be rendered into the /public/about/john.html file."
  [base-directory content-item data]
  (let [write-dir (str (utils/trimr base-directory "/") "/public/")
        to-write (selmer/render (:contents content-item) data)]
    (io/make-parents (str write-dir (:path content-item)))
    (spit (str write-dir (:path content-item)) to-write)))

(defn- write->md!
  "Writes a Markdown file into the /public directory based on its
  path and filename of the file.

  For example, a Markdown file located in /blog/hello-world.md will
  be rendered into the /public/blog/hello-world/index.html file."
  [base-directory layout content-item data]
  (let [write-dir (str (utils/trimr base-directory "/") "/public/")
        to-write (selmer/render layout (merge data {:content content-item}))]
    (io/make-parents (str write-dir (:path content-item) "/index.html"))
    (spit (str write-dir (:path content-item) "/index.html") to-write)))

(defn- write!
  "Writes a given `content-item` into the /public directory based on
  its path and filename of the file. Depending on whether or not it
  is a Selmer template or a Markdown content file, a different
  strategy will be chosen.

  A Selmer template will be rendered as-is, fused with `data`, and
  thus will be dynamic - while a Markdown content file will be
  rendered into the given `layout` (which in itself is a Selmer template)
  and is static."
  [base-directory layout content-item data]
  (if (:selmer content-item)
    (write->sel! base-directory content-item data)
    (write->md! base-directory layout content-item data)))

(defn- empty-public-dir!
  "Recursively deletes all files and directories from within the
  public directory."
  [base-directory]
  (-> (str (utils/trimr base-directory "/") "/public")
      (utils/delete-files-in-path!)))

(defn- build-home!
  "Builds a home page / root page (index.html) into the /public
  directory which is simply the `layout` merged with given `data`,
  and provides the `is_home` template variable to be used from `layout`
  for knowing whether we are dealing with a home page.

  This will be overwritten if there is an index.html.sel file in the
  root directory."
  [base-directory layout data]
  (let [home-data (merge {:is_home true} data)
        home-content-item {:contents layout
                           :path     "index.html"}]
    (println "Building /")
    (write->sel! base-directory home-content-item home-data)))

(defn- build-content!
  "Builds all the given `content` into the /public directory."
  [base-directory layout content data]
  (doseq [content-item content]
    (println "Building" (:path content-item))
    (write! base-directory layout content-item data)))

(defn- copy-assets!
  "Copies all the assets from the `base-directory` to the
  /public directory."
  [base-directory]
  (doseq [file (scan-assets base-directory)]
    (let [write-dir (utils/trimr base-directory "/")
          from ^File (io/file (:path file))
          file-path (-> (:path file)
                        (str/replace base-directory "")
                        (utils/triml "/"))
          to ^File (io/file (str write-dir "/public/" file-path))]
      (println "Copying" file-path)
      (io/make-parents (str write-dir "/public/" file-path))
      (io/copy from to))))

(defn- build!
  "Builds the static site in `base-directory` with `config`."
  [base-directory config]
  (let [content (construct-content base-directory)
        data (construct-templating-data base-directory config)
        layout (get-template base-directory "layout")]
    (empty-public-dir! base-directory)
    (build-home! base-directory layout data)
    (build-content! base-directory layout content data)
    (copy-assets! base-directory)))

(defn- create-base-project-file!
  "Copies a given `stream` into `base-directory` based on `entry`."
  [base-directory stream ^ZipEntry entry]
  (let [path (-> (str base-directory "/" (.getName entry))
                 (str/replace "babe-base-project-master/" ""))
        file (io/file path)]
    (when-not (or (.isDirectory entry)
                  (= path (str base-directory "/.gitignore"))
                  (= path (str base-directory "/LICENSE.txt")))
      (io/make-parents path)
      (io/copy stream file))))

(defn- create-base-project!
  "Download a `base-project-zip-url` and extracts the contents
  into `base-directory`."
  [base-directory]
  (with-open [stream (-> @(client/request {:url base-project-zip-url
                                           :as  :stream})
                         (:body)
                         (ZipInputStream.))]
    (loop [entry (.getNextEntry stream)]
      (when entry
        (let [directory (utils/trimr base-directory "/")]
          (create-base-project-file! directory stream entry)
          (recur (.getNextEntry stream)))))))

(defn- watch!
  "Runs an infinite loop that checks every 1s for any changes
  to files, upon which it will call `(build!)`."
  [base-directory]
  (let [watch-list (atom (scan-watchlist base-directory))]
    (println "Watching ...")
    (future
      (while true
        (let [new-watch-list (scan-watchlist base-directory)]
          (when (not (= @watch-list new-watch-list))
            (build! base-directory (get-config base-directory))
            (reset! watch-list new-watch-list))
          (Thread/sleep 1000))))))

(defn -main
  [& args]
  (let [watching? (contains? (set args) "watch")
        init? (contains? (set args) "init")
        base-directory "./"]
    (cond init?
          (create-base-project! base-directory)
          watching?
          (watch! base-directory)
          :else
          (do
            (build! base-directory (get-config base-directory))
            (System/exit 0)))))
