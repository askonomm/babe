(ns babe.utils
  (:require [clojure.string :as str]
            [clojure.instant :as inst]
            [clojure.java.io :as io]))

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
  (str/trim (str/replace contents #"(?s)^---(.*?)---*" "")))

(defn scan
  [directory when-pred]
  (flatten
    (for
      [file (.list (io/file directory))
       :let [read-dir (trimr directory "/")
             path (str read-dir "/" file)]
       :when (when-pred read-dir path)]
      (if (.isDirectory (io/file path))
        (scan path when-pred)
        {:path  (-> path
                    (triml "/")
                    (trimr "/"))
         :mtime (.lastModified (io/file path))}))))
