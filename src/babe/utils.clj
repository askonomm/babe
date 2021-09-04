(ns babe.utils
  (:require [clojure.string :as str]
            [clojure.instant :as inst]
            [clojure.java.io :as io]
            [markdown.core :as md])
  (:import (java.io File)))

(defn triml
  "Trims the given `trim-char` from the left of `string`."
  [string trim-char]
  (if (clojure.string/starts-with? string trim-char)
    (-> (subs string 1)
        (triml trim-char))
    string))

(defn trimr
  "Trims the given `trim-char` from the right of `string`."
  [string trim-char]
  (if (clojure.string/ends-with? string trim-char)
    (-> (subs string 0 (- (count string) 1))
        (trimr trim-char))
    string))

(defn- parse-md-metadata-value-by-key
  "Parses YAML metadata values depending on key."
  [value key]
  (cond (= "date" key)
        (inst/read-instant-date value)
        :else value))

(defn- parse-md-metadata-line
  "Parses each YAML metadata line into a map."
  [line]
  (let [key (str/trim (first (str/split line #":")))
        value (str/trim (second (str/split line #":")))]
    {(keyword key)
     (parse-md-metadata-value-by-key value key)}))

(defn parse-md-metadata
  "Takes in a given `content` as the entirety of a Markdown
  content file, and then parses YAML metadata from it."
  [contents]
  (if-let [match (re-find #"(?s)^(---)(.*?)(---|\.\.\.)" contents)]
    (let [lines (remove #(= "---" %) (str/split-lines (first match)))]
      (into {} (map #(parse-md-metadata-line %) lines)))
    {}))

(defn parse-md-entry
  "Takes in a given `content` as the entirety of a Markdown
  content file, and then parses the Markdown into HTML from it."
  [contents]
  (-> contents
      (str/replace #"(?s)^---(.*?)---*" "")
      (str/trim)
      (md/md-to-html-string)))

(defn scan
  "Scans a given `directory` according to `when-pred`, and
  returns a list of maps containing the path of a file and
  the modified time of said file."
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
  "Deletes all files and folders from within the given `path`,
  but does not delete the path itself."
  [path]
  (doseq [file-in-dir (.listFiles (io/file path))]
    (if (.isDirectory ^File file-in-dir)
      (delete-files-in-path! (.getPath ^File file-in-dir))
      (io/delete-file file-in-dir))))