(ns resend.unit.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [resend.internal.util :as util]))

;; ---------------------------------------------------------------------------
;; clj->api
;; ---------------------------------------------------------------------------

(deftest clj->api-test
  (testing "Deve converter keywords de kebab-case para snake_case"
    (let [input {:api-key "re_123" :reply-to "test@example.com"}
          expected {:api_key "re_123" :reply_to "test@example.com"}]
      (is (= expected (util/clj->api input)))))

  (testing "Deve manter keywords sem hífen inalteradas"
    (let [input {:from "one@example.com" :subject "Example"}
          expected {:from "one@example.com" :subject "Example"}]
      (is (= expected (util/clj->api input)))))

  (testing "Deve retornar o próprio valor se não for uma keyword"
    (let [input {"api-key" "re_123"
                 123 "numeric-key"
                 nil nil
                 :from "one@example.com"
                 :reply-to "test@example.com"
                 :subject "Example"}
          expected {"api-key" "re_123"
                    123 "numeric-key"
                    nil nil
                    :from "one@example.com"
                    :reply_to "test@example.com"
                    :subject "Example"}]
      (is (= expected (util/clj->api input)))))

  (testing "Deve transformar chaves de mapas aninhados e vetores de mapas"
    (let [input {:email-data {:sender-name "Lais"
                              :tags [{:tag-name "welcome" :tag-value "true"}]}
                 :scheduled-at "2026-08-21"}
          expected {:email_data {:sender_name "Lais"
                                 :tags [{:tag_name "welcome" :tag_value "true"}]}
                    :scheduled_at "2026-08-21"}]
      (is (= expected (util/clj->api input)))))

  (testing "Deve manter valores intactos (incluindo strings, números, booleans e nils)"
    (let [input {:user-id 101
                 :is-active true
                 :middle-name nil
                 :roles ["admin" "user"]}
          expected {:user_id 101
                    :is_active true
                    :middle_name nil
                    :roles ["admin" "user"]}]
      (is (= expected (util/clj->api input)))))

  (testing "Deve lidar com mapas vazios"
    (is (= {} (util/clj->api {})))))

;; ---------------------------------------------------------------------------
;; api->clj
;; ---------------------------------------------------------------------------

(deftest api->clj-test
  (testing "Deve converter keywords de snake_case para kebab-case"
    (let [input {:api_key "re_123" :reply_to "test@example.com"}
          expected {:api-key "re_123" :reply-to "test@example.com"}]
      (is (= expected (util/api->clj input)))))

  (testing "Deve manter keywords sem hífen inalteradas"
    (let [input {:from "one@example.com" :subject "Example"}
          expected {:from "one@example.com" :subject "Example"}]
      (is (= expected (util/api->clj input)))))

  (testing "Deve retornar o próprio valor se não for uma keyword"
    (let [input {"api_key" "re_123"
                 123 "numeric_key"
                 nil nil
                 :from "one@example.com"
                 :reply_to "test@example.com"
                 :subject "Example"}
          expected {"api_key" "re_123"
                    123 "numeric_key"
                    nil nil
                    :from "one@example.com"
                    :reply-to "test@example.com"
                    :subject "Example"}]
      (is (= expected (util/api->clj input)))))

  (testing "Deve transformar chaves de mapas aninhados e vetores de mapas"
    (let [input {:email_data {:sender_name "Lais"
                              :tags [{:tag_name "welcome" :tag_value "true"}]}
                 :scheduled_at "2026-08-21"}
          expected {:email-data {:sender-name "Lais"
                                 :tags [{:tag-name "welcome" :tag-value "true"}]}
                    :scheduled-at "2026-08-21"}]
      (is (= expected (util/api->clj input)))))

  (testing "Deve manter valores intactos (incluindo strings, números, booleans e nils)"
    (let [input {:user_id 101
                 :is_active true
                 :middle_name nil
                 :roles ["admin" "user"]}
          expected {:user-id 101
                    :is-active true
                    :middle-name nil
                    :roles ["admin" "user"]}]
      (is (= expected (util/api->clj input)))))

  (testing "Deve lidar com mapas vazios"
    (is (= {} (util/api->clj {})))))

;; ---------------------------------------------------------------------------
;; remove-nils
;; ---------------------------------------------------------------------------

(deftest remove-nils-basic
  (testing "nil values are removed from the top level"
    (is (= {:a 1 :b "hello"}
           (util/remove-nils {:a 1 :b "hello" :c nil :d nil})))))

(deftest remove-nils-keeps-false
  (testing "false values are retained (falsy ≠ nil)"
    (is (= {:active false}
           (util/remove-nils {:active false :gone nil})))))

;; ---------------------------------------------------------------------------
;; build-query-string
;; ---------------------------------------------------------------------------

(deftest build-query-string-nil-returns-empty
  (testing "nil params returns empty string"
    (is (= "" (util/build-query-string nil)))))

(deftest build-query-string-empty-map-returns-empty
  (testing "empty map returns empty string"
    (is (= "" (util/build-query-string {})))))

(deftest build-query-string-single-scalar
  (testing "single scalar param produces ?key=value"
    (is (= "?limit=10" (util/build-query-string {:limit 10})))))

(deftest build-query-string-kebab-to-snake
  (testing "kebab-case keys are converted to snake_case"
    (let [qs (util/build-query-string {:start-date "2026-07-01"})]
      (is (clojure.string/includes? qs "start_date="))
      (is (not (clojure.string/includes? qs "start-date="))))))

(deftest build-query-string-sequential-values-repeated
  (testing "sequential values produce repeated params"
    (let [qs (util/build-query-string {:metrics ["sent" "delivered"]})]
      (is (clojure.string/includes? qs "metrics=sent"))
      (is (clojure.string/includes? qs "metrics=delivered")))))

(deftest build-query-string-nil-values-omitted
  (testing "nil values are omitted from the query string"
    (let [qs (util/build-query-string {:limit 5 :after nil})]
      (is (clojure.string/includes? qs "limit=5"))
      (is (not (clojure.string/includes? qs "after"))))))

(deftest build-query-string-starts-with-question-mark
  (testing "non-empty params string starts with ?"
    (is (clojure.string/starts-with?
         (util/build-query-string {:limit 1}) "?"))))