(ns babe.utils
  (:require [clojure.string :as string]
            [clojure.instant :as inst]
            [clojure.java.io :as io]
            [markdown.core :as md])
  (:import (clojure.lang PersistentList)))


(defn triml
  "Trims the given `trim-char` from the left of `string`."
  [string trim-char]
  (loop [string string]
    (if (string/starts-with? string trim-char)
      (recur (subs string 1))
      string)))


(defn trimr
  "Trims the given `trim-char` from the right of `string`."
  [string trim-char]
  (loop [string string]
    (if (string/ends-with? string trim-char)
      (recur (subs string 0 (- (count string) 1)))
      string)))


(defn- parse-md-metadata-value-by-key
  "Parses YAML metadata values depending on key."
  [value key]
  (cond (= "date" key)
        (inst/read-instant-date value)
        :else value))


(defn- parse-md-metadata-line
  "Parses each YAML metadata line into a map."
  [line]
  (let [key (-> (string/split line #":")
                first
                string/trim)
        value (->> (string/split line #":")
                   next
                   (string/join ":")
                   string/trim)]
    {(keyword key)
     (parse-md-metadata-value-by-key value key)}))


(defn parse-md-metadata
  "Takes in a given `content` as the entirety of a Markdown
  content file, and then parses YAML metadata from it."
  [contents]
  (if-let [match (re-find #"(?s)^(---)(.*?)(---|\.\.\.)" contents)]
    (let [lines (remove #(= "---" %) (string/split-lines (first match)))]
      (into {} (map #(parse-md-metadata-line %) lines)))
    {}))


(defn parse-md-entry
  "Takes in a given `content` as the entirety of a Markdown
  content file, and then parses the Markdown into HTML from it."
  [contents]
  (-> contents
      (string/replace #"(?s)^---(.*?)---*" "")
      (string/trim)
      (md/md-to-html-string)))


(defn scan
  "Scans a given `directory` for any and all files (recursively)
  and returns a list of maps containing the path of a file and
  the modified time of said file."
  [directory]
  (loop [paths (map str (.list (io/file directory)))
         result []]
    (let [path (first paths)
          full-path (str (trimr directory "/") "/" path)]
      (if (empty? paths)
        result
        (if (.isDirectory (io/file full-path))
          (let [new-paths (map #(str path "/" %)
                               (.list (io/file full-path)))]
            (recur (concat (drop 1 paths) new-paths)
                   result))
          (recur (drop 1 paths)
                 (conj result
                       {:path  full-path
                        :mtime (.lastModified (io/file full-path))})))))))


(defn delete-files!
  "Deletes all files and folders from within the given `directory`,
  but does not delete the directory itself."
  [directory]
  (doseq [{:keys [path]} (scan directory)]
    (io/delete-file path)))


(defn argcmd
  "Parses a given list of `args` for a `command` and returns
  `true` if the command was found. If the command has a
  subcommand provided, then it will return that instead."
  [command ^PersistentList args]
  (when (seq? args)
    (let [index (.indexOf args command)]
      (if-not (= -1 index)
        (if-let [subcommand (nth args (+ index 1) nil)]
          subcommand
          true)
        nil))))