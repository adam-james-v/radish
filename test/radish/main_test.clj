(ns radish.main-test
  (:require [clojure.test :as t :refer [deftest is]]
            [radish.main :as rad]))

(def get-title-test-org-strs
  {:a "* ;;\n#+Title: Found Title\n#+AUTHOR: adam-james\n\n* Found Headline as Title\nContent goes here...\n"
   :b "* ;;\n#+AUTHOR: adam-james\n\n* Found Headline as Title\nContent goes here...\n"
   :c "* Valid First Headline\n#+Title: Found Title\n#+AUTHOR: adam-james\n\n* Found Headline as Title\nContent goes here...\n"
   :d "* Valid First Headline\n#+AUTHOR: adam-james\n\n* Found Headline as Title\nContent goes here...\n"
   :e "* Valid First Headline\n#+Title: Found Title\n#+AUTHOR: adam-james\n\n* Found Headline as Title\nContent goes here...\n"
   :f (slurp "radish.org")})

(deftest get-title-test
  (is (= (rad/get-title (:a get-title-test-org-strs)) "Found Title"))
  (is (= (rad/get-title (:b get-title-test-org-strs)) "Found Headline as Title"))
  (is (= (rad/get-title (:c get-title-test-org-strs)) "Found Title"))
  (is (= (rad/get-title (:d get-title-test-org-strs)) "Valid First Headline"))
  (is (= (rad/get-title (:e get-title-test-org-strs)) "Found Title"))
  (is (= (rad/get-title (:f get-title-test-org-strs)) "radish")))

(deftest get-deps-test
  (is (= (rad/get-deps (slurp "radish-logo.org"))
         '{:deps
           {io.github.adam-james-v/svg-clj #:mvn{:version "0.0.3-SNAPSHOT"},
            hiccup/hiccup #:mvn{:version "2.0.0-alpha2"}}}))
  (is (nil? (rad/get-deps (slurp "radish-basic.org")))))
