(ns babe.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [com.climate.claypoole :as cp]
            [selmer.parser :as selmer]
            [babe.utils :as utils])
  (:gen-class))

(defn- scan
  "Recursively scans a given `base-directory` for any .md or .html
  files. Then returns a vector of maps, each map containing the file
  path and modified time. The modified time is used by the watcher
  to know if files have changed, so that it could re-build automatically."
  [base-directory]
  (flatten
    (for
      [file (.list (io/file base-directory))
       :let [read-dir (utils/trimr base-directory "/")
             path (str read-dir "/" file)]
       :when (and (not (= path (str read-dir "/public")))
                  (not (= path (str read-dir "/layout.sel")))
                  (or (str/ends-with? path ".md")
                      (str/ends-with? path ".sel")
                      (.isDirectory (io/file path))))]
      (if (.isDirectory (io/file path))
        (scan path)
        {:path  (utils/triml path "/")
         :mtime (.lastModified (io/file path))}))))

(defn- build-content-item->md
  "Builds the data structure for a Markdown file by taking the file
  `contents` and parsing it for YAML metadata and Markdown data."
  [base-directory path contents]
  {:path  (-> path
              (str/replace base-directory "")
              (utils/triml "/")
              (str/replace ".md" ""))
   :meta  (utils/parse-md-metadata contents)
   :entry (utils/parse-md-entry contents)})

(defn- build-content-item->sel
  "Builds the data structure for a Selmer file. Unlike a
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

(defn- build-content-item
  "Build a content item depending on the extension of the file.
  Currently supports .md for Markdown and .sel for Selmer."
  [base-directory {:keys [path]}]
  (let [contents (slurp path)]
    (cond (str/ends-with? path ".md")
          (build-content-item->md base-directory path contents)
          (str/ends-with? path ".sel")
          (build-content-item->sel base-directory path contents))))

(defn- build-content
  "Iterates over each scanned file and returns a collection of
  built content items."
  [base-directory]
  (pmap (fn [file]
          (build-content-item base-directory file))
        (scan base-directory)))

(defn- build-templating-data-item
  "Builds a templating data item, which is a collection of content
  items from a specified folder. Optionally sorted and ordered, and
  returned as a key-value map to be consumed a Selmer template."
  [base-directory data-item]
  {(keyword (:name data-item))
   (cond->> (-> (str (utils/trimr base-directory "/") "/" (:folder data-item))
                (build-content))
            (:sortBy data-item)
            (sort-by #(get-in % [:meta (keyword (:sortBy data-item))]))
            (= "desc" (:order data-item))
            (reverse))})

(defn- build-templating-data
  "Builds the templating data from the given configuration and
  data-set defined in the babe.json file, allowing for pretty
  dynamic use."
  [base-directory config]
  {:site (:site config)
   :data (into {} (map #(build-templating-data-item base-directory %)
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
  its path and filename of the file. Depending on whether or not it is
  a Selmer template or a Markdown content file, a different strategy
  will be chosen.

  A Selmer template will be rendered as-is, fused with `data`, and thus
  will be dynamic - while a Markdown content file will be rendered into
  the given `layout` (which in itself is a Selmer template) and is static."
  [base-directory layout content-item data]
  (if (:selmer content-item)
    (write->sel! base-directory content-item data)
    (write->md! base-directory layout content-item data)))

(defn- build-home!
  "Builds a home page / root page (/index.html) into the /public directory."
  [base-directory layout data]
  (let [home-data (merge {:is_home true} data)
        home-content-item {:contents layout
                           :path     "index.html"}]
    (write->sel! base-directory home-content-item home-data)))

(defn- build-content!
  "Builds all of the content files into the /public directory."
  [base-directory layout content data]
  (cp/pdoseq :builtin [content-item content]
             (write! base-directory layout content-item data)))

(defn- move-css!
  "Moves all of the CSS files into the /public directory."
  [base-directory])

(defn- move-js!
  "Moves all of the JS files into the /public directory."
  [base-directory])

(defn- build!
  [base-directory config]
  (let [content (build-content base-directory)
        data (build-templating-data base-directory config)
        layout (get-template base-directory "layout")]
    (build-home! base-directory layout data)
    (build-content! base-directory layout content data)
    (move-css! base-directory)
    (move-js! base-directory)))

(defn- watch!
  [base-directory config]
  (println "Watching ...")
  (let [watch-list (atom (scan base-directory))]
    (repeatedly
      #(let [new-watch-list (scan base-directory)]
         (when (not (= @watch-list new-watch-list))
           (build! base-directory config)
           (reset! watch-list new-watch-list))
         (Thread/sleep 1000)))))

(defn -main
  [& args]
  (let [base-directory "../bien.ee"
        config (get-config base-directory)]
    (build! base-directory config)))
