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
  (is (= (rad/get-deps (slurp "radish.org"))
         '{:deps
          {org.clojure/clojure #:mvn{:version "1.10.3"},
           org.clojure/tools.cli #:mvn{:version "1.0.206"},
           hiccup/hiccup #:mvn{:version "2.0.0-alpha2"},
           orgmode/orgmode
           {:git/url "https://github.com/bnbeckwith/orgmode",
            :sha "722972c72b43c18a5cdbbc9c3e392b5ee9e2b503"}}})))
