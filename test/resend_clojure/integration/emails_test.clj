
(ns resend-clojure.integration.emails-test
  "Integration tests that exercise the real Resend API.
 
  These tests are SKIPPED automatically when the RESEND_API_KEY environment
  variable is not set, so they never block a standard `lein test` run in CI
  without credentials.
 
  To run them locally:
    RESEND_API_KEY=re_xxx lein test :only resend-clojure.emails-integration-test
 
  The :to address uses Resend's built-in test address that always succeeds
  without sending a real email: delivered@resend.dev
 
  References:
  - Resend test addresses: https://resend.com/docs/dashboard/emails/send-test-emails"
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [resend-clojure.core   :as resend]
            [resend-clojure.api.emails :as emails]))

;; ---------------------------------------------------------------------------
;; Guard – skip when no API key is present
;; ---------------------------------------------------------------------------

(defn- api-key []
  (System/getenv "RESEND_API_KEY"))

(defn- skip-without-api-key [f]
  (if (seq (api-key))
    (f)
    (println "  [SKIP] RESEND_API_KEY not set – skipping integration tests")))

(use-fixtures :once skip-without-api-key)

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- live-client []
  (resend/create-client {:api-key (api-key)}))

;; Resend's built-in sink address – always delivers, never spams.
(def ^:private test-recipient "delivered@resend.dev")
(def ^:private test-sender    "onboarding@resend.dev")

;; ---------------------------------------------------------------------------
;; Integration tests
;; ---------------------------------------------------------------------------

(deftest integration-send-email
  (testing "send returns a valid email id for a real API call"
    (when (seq (api-key))
      (let [result (emails/send! (live-client)
                                 {:from    test-sender
                                  :to      test-recipient
                                  :subject "resend-clojure integration test"
                                  :text    "Integration test – please ignore."})]
        (is (nil? (:error result))
            (str "Unexpected error: " (:error result)))
        (is (string? (get-in result [:data :id]))
            "Expected a string email id in :data :id")))))

(deftest integration-send-email-with-html
  (testing "send accepts HTML body"
    (when (seq (api-key))
      (let [result (emails/send! (live-client)
                                 {:from    test-sender
                                  :to      test-recipient
                                  :subject "resend-clojure HTML test"
                                  :html    "<h1>Hello from resend-clojure!</h1>"})]
        (is (nil? (:error result)))
        (is (string? (get-in result [:data :id])))))))

(deftest integration-get-email
  (testing "get-email retrieves a previously sent email"
    (when (seq (api-key))
      (let [sent   (emails/send! (live-client)
                                 {:from    test-sender
                                  :to      test-recipient
                                  :subject "resend-clojure get test"
                                  :text    "get test"})
            email-id (get-in sent [:data :id])
            result (emails/get-email! (live-client) email-id)]
        (is (nil? (:error result)))
        (is (= email-id (get-in result [:data :id])))))))

(deftest integration-send-batch
  (testing "send-batch delivers multiple emails in one call"
    (when (seq (api-key))
      (let [batch  [{:from    test-sender
                     :to      test-recipient
                     :subject "Batch 1 – resend-clojure"
                     :text    "batch item 1"}
                    {:from    test-sender
                     :to      test-recipient
                     :subject "Batch 2 – resend-clojure"
                     :text    "batch item 2"}]
            result (emails/send-batch! (live-client) batch)]
        (is (nil? (:error result)))
        (is (= 2 (count (get-in result [:data :data]))))))))

(deftest integration-invalid-api-key
  (testing "invalid API key returns error envelope"
    (let [bad-client (resend/create-client {:api-key "re_INVALID_KEY"})
          result     (emails/send! bad-client
                                   {:from    test-sender
                                    :to      test-recipient
                                    :subject "Should fail"
                                    :text    "Should fail"})]
      (is (nil? (:data result)))
      (is (some? (:error result))))))