(ns babe.utils
  (:require [clojure.string :as str]
            [clojure.instant :as inst]
            [clojure.java.io :as io]
            [markdown.core :as md]))

(defn triml [string trim-char]
  (if (clojure.string/starts-with? string trim-char)
    (-> (subs string 1)
        (triml trim-char))
    string))

(defn trimr [string trim-char]
  (if (clojure.string/ends-with? string trim-char)
    (-> (subs string 0 (- (count string) 1))
        (trimr trim-char))
    string))

(defn- parse-md-metadata-value-by-key [value key]
  (cond (= "date" key)
        (inst/read-instant-date value)
        :else value))

(defn- parse-md-metadata-line [line]
  (let [key (str/trim (first (str/split line #":")))
        value (str/trim (second (str/split line #":")))]
    {(keyword key)
     (parse-md-metadata-value-by-key value key)}))

(defn parse-md-metadata
  "Lorem ipsum dolor sit amet."
  [contents]
  (if-let [match (re-find #"(?s)^(---)(.*?)(---|\.\.\.)" contents)]
    (let [lines (remove #(= "---" %) (str/split-lines (first match)))]
      (into {} (map #(parse-md-metadata-line %) lines)))
    {}))

(defn parse-md-entry
  "Lorem ipsum dolor sit amet."
  [contents]
  (-> contents
      (str/replace #"(?s)^---(.*?)---*" "")
      (str/trim)
      (md/md-to-html-string)))

(defn scan
  [directory when-pred]
  (flatten
    (for
      [file (.list (io/file directory))
       :let [read-dir (trimr directory "/")
             path (str read-dir "/" file)]
       :when (when-pred read-dir (str/lower-case path))]
      (if (.isDirectory (io/file path))
        (scan path when-pred)
        {:path  (-> path
                    (triml "/")
                    (trimr "/"))
         :mtime (.lastModified (io/file path))}))))

(defn delete-files-in-path!
  [path]
  (doseq [file-in-dir (.listFiles (io/file path))]
    (if (.isDirectory file-in-dir)
      (delete-files-in-path! (.getPath file-in-dir))
      (io/delete-file file-in-dir))))