(ns babe.utils-test
  (:require [clojure.test :refer :all]
            [babe.utils :refer :all]))

(deftest triml-test
  (testing "Trim when there's one character on the left."
    (is (= "oneontheleft"
           (triml "-oneontheleft" "-"))))
  (testing "Do not trim when there is no character on the left."
    (is (= "noneontheleft"
           (triml "noneontheleft" "-"))))
  (testing "Trim all when there are multiple characters on the left."
    (is (= "multipleontheleft"
           (triml "-----multipleontheleft" "-")))))

(deftest trimr-test
  (testing "Trim when there's one character on the right."
    (is (= "oneontheright"
           (trimr "oneontheright-" "-"))))
  (testing "Do not trim when there is no character on the right."
    (is (= "noneontheright"
           (trimr "noneontheright" "-"))))
  (testing "Trim all when there are multiple characters on the right."
    (is (= "multipleontheright"
           (trimr "multipleontheright-----" "-")))))

(deftest parse-md-metadata-test
  (testing "Passing a correct metadata string"
    (let [result (parse-md-metadata "---\ntitle: hi there\n---")
          expected {:title "hi there"}]
      (is (= expected result))))
  (testing "Passing a correct metadata string with date key"
    (let [result (parse-md-metadata "---\ntitle: hi there\ndate: 2020-10-05\n---")
          expected {:title "hi there"
                    :date #inst "2020-10-05T00:00:00.000-00:00"}]
      (is (= expected result))))
  (testing "Passing an incorrect metadata string"
    (let [result (parse-md-metadata "incorrectdata")
          expected {}]
      (is (= expected result)))))

(deftest parse-md-entry-test
  (testing "Passing contents without metadata"
    (let [result (parse-md-entry "This is some content.")
          expected "<p>This is some content.</p>"]
      (is (= expected result))))
  (testing "Passing contents with metadata"
    (let [result (parse-md-entry "---\ntitle: hi there\n---\nThis is some content.")
          expected "<p>This is some content.</p>"]
      (is (= expected result))))
  (testing "Passing only metadata, but no contents"
    (let [result (parse-md-entry "---\ntitle: hi there\n---")
          expected ""]
      (is (= expected result))))
  (testing "Passing nothing at all"
    (let [result (parse-md-entry "")
          expected ""]
      (is (= expected result)))))

(deftest argcmd-test
  (testing "Passing a command"
    (let [result (argcmd "command" (list "command"))
          expected true]
      (is (= expected result))))
  (testing "Passing a command with a subcommand"
    (let [result (argcmd "command" (list "command" "subcommand"))
          expected "subcommand"]
      (is (= expected result))))
  (testing "Passing an empty list"
    (let [result (argcmd "command" nil)
          expected nil]
      (is (= expected result)))))