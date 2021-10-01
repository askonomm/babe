(ns babe.core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.java.shell :as sh]
            [selmer.parser :as selmer]
            [selmer.util :as selmer.util]
            [babe.utils :as utils]
            [org.httpkit.client :as client])
  (:gen-class)
  (:import (java.time.format DateTimeFormatter)
           (java.util.zip ZipInputStream ZipEntry)
           (java.util Date)
           (java.time ZoneId)))


(set! *warn-on-reflection* true)


; By default, Selmer escapes all HTML entities.
; We don't want that.
(selmer.util/turn-off-escaping!)


(def ^:private base-project-zip-url
  "https://github.com/askonomm/babe-base-project/archive/refs/heads/master.zip")


(defn- scan-assets
  "Recursively scans a given `base-directory` for any asset files
   like CSS, JS, images, etc. The result of this is used to know
   which files to copy to the /public directory."
  [base-directory]
  (->> (utils/scan base-directory)
       (filter (fn [{:keys [path]}]
                 (and (not (string/starts-with? path (str base-directory "/public")))
                      (not (= path (str base-directory "/layout.sel")))
                      (not (= path (str base-directory "/babe.json")))
                      (not (string/starts-with? path (str base-directory "/.")))
                      (not (string/ends-with? path ".sel"))
                      (not (string/ends-with? path ".md")))))))

(defn- scan-content
  "Recursively scans a given `base-directory` for any .md or .html
  files. The result of this is used for generating content files."
  [base-directory]
  (->> (utils/scan base-directory)
       (filter (fn [{:keys [path]}]
                 (and (not (string/starts-with? path (str base-directory "/public")))
                      (not (= path (str base-directory "/layout.sel")))
                      (or (string/ends-with? path ".md")
                          (string/ends-with? path ".sel")))))))


(defn- scan-watchlist
  "Recursively scans a given `base-directory` for any and all files,
  except those in the /public directory. The result of this is used
  by the watcher to know when any file was modified, to then trigger
  a re-build."
  [base-directory]
  (->> (utils/scan base-directory)
       (filter (fn [{:keys [path]}]
                 (and (not (string/starts-with? path (str base-directory "/public")))
                      (not (string/starts-with? path (str base-directory "/.")))
                      (not (= path (str base-directory "/babe.json"))))))))

(defn- construct-content-item->md
  "Constructs the data structure for a Markdown file by taking the file
  `contents` and parsing it for YAML metadata and Markdown data."
  [base-directory path contents]
  {:path  (-> path
              (string/replace-first base-directory "")
              (utils/triml "/")
              (string/replace ".md" ""))
   :meta  (utils/parse-md-metadata contents)
   :entry (utils/parse-md-entry contents)})


(defn- construct-content-item->sel
  "Constructs the data structure for a Selmer file. Unlike a
  Markdown file, we will not be creating an index.html file to
  put the rendered contents into, but rather just remove the .sel
  extension and create a file based on the content file name itself,
  hence the :selmer part, so that the builder will know what to do."
  [base-directory path contents]
  {:path     (-> path
                 (string/replace-first base-directory "")
                 (utils/triml "/")
                 (string/replace ".sel" ""))
   :selmer   true
   :contents contents})


(defn- construct-content-item
  "Build a content item depending on the extension of the file.
  Currently, supports .md for Markdown and .sel for Selmer."
  [base-directory {:keys [path]}]
  (let [contents (slurp path)]
    (cond
      (string/ends-with? path ".md")
      (construct-content-item->md base-directory path contents)
      (string/ends-with? path ".sel")
      (construct-content-item->sel base-directory path contents))))


(defn- construct-content
  "Iterates over each scanned file and returns a collection of
  constructed content items."
  [base-directory]
  (pmap (fn [file]
          (construct-content-item base-directory file))
        (scan-content base-directory)))


(defn- prepend-path-to-content-item-path
  "Prepends a given `path` to the :path of each of the items
  in `content`"
  [path content]
  (map (fn [content-item]
         (merge content-item
                {:path (str path "/" (:path content-item))}))
       content))


(defn- construct-templating-data-item-content-item
  "Given a single `path`, it will then either retrieve a single content
   item if the path ends with a .md extension, or it will retrieve all the
   content it can find in a given directory if it does not contain a .md 
   extension."
  [base-directory path]
  (let [path (-> path
                 (utils/triml "/")
                 (utils/trimr "/"))]
    (if (string/ends-with? path ".md")
      (construct-content-item base-directory {:path (str base-directory "/" path)})
      (->> (construct-content (str base-directory "/" path))
           (prepend-path-to-content-item-path path)))))


(defn- construct-templating-data-item-content
  "Turns given path(s) of `content` into actual content."
  [base-directory content]
  (if (vector? content)
    (mapv #(construct-templating-data-item-content-item base-directory %)
          content)
    (construct-templating-data-item-content-item base-directory content)))


(defn- group-content->date
  "Given a #inst date as `val` and a date format pattern as `mod`, it 
   will attempt to return the result of the date formatting."
  [val mod]
  (let [date (-> (.toInstant ^Date val)
                 (.atZone (ZoneId/systemDefault))
                 (.toLocalDateTime))
        formatter (DateTimeFormatter/ofPattern mod)]
    (.format formatter date)))

(defn- group-content
  "A grouping function passed to `group-by` that attempts to group content
   by a given content-item metadata as `group-by`. Special form of `group-by` 
   can be passed as well:
   
   `date|format` can be passed to group by date where the `format` can be any 
   formatting string that Java's DateFormatter accepts, such as YYYY for a year,
   dd for a day, and so forth."
  [content-item group-by]
  (let [key (if (string/includes? "|" group-by)
              (first (string/split group-by #"|"))
              group-by)
        val (get-in content-item [:meta (keyword key)])
        mod (when (string/includes? "|" group-by)
              (last (string/split group-by #"|")))]
    (if (nil? mod)
      val
      (cond
        (= "date" key)
        (group-content->date val mod)))))


(defn- construct-templating-data-item
  "Builds a templating data item, which is a collection of content
  items from a specified path. Optionally sorted, ordered or grouped, and
  returned as a key-value map to be consumed by a Selmer template."
  [base-directory [k v]]
  (let [content (construct-templating-data-item-content base-directory
                                                        (:content v))]
    {k (if (map? content)
         content
         (cond->> content
                  (:sortBy v)
                  (sort-by #(get-in % [:meta (keyword (:sortBy v))]))
                  (= "desc" (:order v))
                  (reverse)
                  (:groupBy v)
                  (group-by #(group-content % (:groupBy v)))))}))


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
    (-> (str base-directory "/" template ".sel")
        (slurp))
    (catch Exception _
      "")))


(defn- get-config
  "Attempts to retrieve the `babe.json` contents and parse it
  into EDN. If it cannot find the file, will return an empty
  map instead."
  [base-directory]
  (try
    (-> (str base-directory "/babe.json")
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
        to-write (try
                   (selmer/render (:contents content-item) data)
                   (catch Exception e
                     (println "There's an error with your Selmer template:")
                     (println (:cause (Throwable->map e)))))]
    (io/make-parents (str write-dir (:path content-item)))
    (spit (str write-dir (:path content-item)) to-write)))


(defn- write->md!
  "Writes a Markdown file into the /public directory based on its
  path and filename of the file.

  For example, a Markdown file located in /blog/hello-world.md will
  be rendered into the /public/blog/hello-world/index.html file."
  [base-directory layout content-item data]
  (let [write-dir (str (utils/trimr base-directory "/") "/public/")
        to-write (try
                   (selmer/render layout (merge data {:content content-item}))
                   (catch Exception e
                     (println "There's an error with your Selmer template:")
                     (println (:cause (Throwable->map e)))))]
    (io/make-parents (str write-dir (:path content-item) "/index.html"))
    (spit (str write-dir (:path content-item) "/index.html") to-write)))


(defn- write!
  "Writes a given `content-item` into the /public directory based on
  its path and filename of the file. Depending on whether it
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
      (utils/delete-files!)))


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
    (let [from (io/file (:path file))
          file-path (-> (:path file)
                        (string/replace base-directory ""))
          to (io/file (str base-directory "/public/" file-path))]
      (println "Copying" file-path)
      (io/make-parents (str base-directory "/public/" file-path))
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


(defn- build-and-exit!
  "Builds the static site in `base-directory` and exits."
  [base-directory]
  (build! base-directory (get-config base-directory))
  (System/exit 0))


(defn- create-base-project-file!
  "Copies a given `stream` into `base-directory` based on `entry`."
  [base-directory stream ^ZipEntry entry]
  (let [path (-> (str base-directory "/" (.getName entry))
                 (string/replace "babe-base-project-master/" ""))
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
  (println "Creating base project ...")
  (with-open [stream (-> @(client/request {:url base-project-zip-url
                                           :as  :stream})
                         (:body)
                         (ZipInputStream.))]
    (loop [entry (.getNextEntry stream)]
      (when-not entry
        (println "... done"))
      (when entry
        (create-base-project-file! base-directory stream entry)
        (recur (.getNextEntry stream))))))


(defn- watch!
  "Runs an infinite loop that checks every 1s for any changes
  to files, upon which it will call `(build!)`."
  [base-directory]
  (println "Watching ...")
  (loop [watch-list (scan-watchlist base-directory)
         new-watch-list (scan-watchlist base-directory)]
    (Thread/sleep 1000)
    (when-not (= watch-list new-watch-list)
      (build! base-directory (get-config base-directory)))
    (recur new-watch-list
           (scan-watchlist base-directory))))


(defn -main
  [& args]
  (let [current-directory (-> (:out (sh/sh "pwd"))
                              (string/replace "\n" ""))
        base-directory (if (utils/argcmd "dir" args)
                         (-> (utils/argcmd "dir" args)
                             (utils/trimr "/"))
                         (-> current-directory
                             (utils/trimr "/")))
        arg-init (utils/argcmd "init" args)
        arg-watch (utils/argcmd "watch" args)]
    (cond
      ; create a base project in the current dir
      (= true arg-init)
      (create-base-project! base-directory)
      ; create a base project in specified dir
      (string? arg-init)
      (create-base-project! arg-init)
      ; watch
      arg-watch
      (watch! base-directory)
      ; build
      :else
      (build-and-exit! base-directory))))
