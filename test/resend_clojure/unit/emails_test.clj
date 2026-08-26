(ns resend-clojure.unit.emails-test
  "Unit tests for resend-clojure.emails.

  All tests use MockAdapter – no real HTTP calls are made.

  References:
  - Resend email API: https://resend.com/docs/api-reference/emails/send-email"
  (:require [clojure.test :refer [deftest testing is]]
            [cheshire.core :as json]
            [resend-clojure.api.emails :as emails]
            [resend-clojure.internal.client :as client]
            [resend-clojure.internal.http-mock-adapter :as mock]))

;; ---------------------------------------------------------------------------
;; Test fixtures / helpers
;; ---------------------------------------------------------------------------

(def ^:private base-url "https://api.resend.com")

(defn- create-client-with
  "Builds a test client wired to the given MockAdapter."
  [adapter]
  (client/create-client {:api-key "re_test_key"
                         :adapter adapter}))

(defn- success-adapter
  "Returns an adapter pre-loaded with a successful send response."
  []
  (mock/make-mock-adapter
   [{:status 200
     :body   {:id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"}}]))

(defn- error-adapter
  "Returns an adapter that simulates an API error (e.g. invalid API key)."
  []
  (mock/make-mock-adapter
   [{:status 401
     :body   {:name    "missing_api_key"
              :message "API key is invalid or missing"
              :status-code 401}}]))

(def ^:private valid-params
  {:from    "Acme <no-reply@acme.com>"
   :to      ["user@example.com"]
   :subject "Unit test email"
   :html    "<p>Hello from tests</p>"})

;; ---------------------------------------------------------------------------
;; emails/send!
;; ---------------------------------------------------------------------------

(deftest send-returns-success-envelope
  (testing "successful send returns {:data {:id ...} :error nil}"
    (let [adapter (success-adapter)
          client  (create-client-with adapter)
          result  (emails/send! client valid-params)]
      (is (nil? (:error result)))
      (is (= "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
             (get-in result [:data :id]))))))

(deftest send-uses-post-method
  (testing "send issues a POST request"
    (let [adapter (success-adapter)
          client  (create-client-with adapter)]
      (emails/send! client valid-params)
      (is (= :post (:method (mock/last-call adapter)))))))

(deftest send-hits-correct-url
  (testing "send targets POST /emails"
    (let [adapter (success-adapter)
          client  (create-client-with adapter)]
      (emails/send! client valid-params)
      (is (= (str base-url "/emails")
             (:url (mock/last-call adapter)))))))

(deftest send-serialises-body-as-snake-case
  (testing "request body has snake_case keys (not kebab-case)"
    (let [params  (assoc valid-params :reply-to ["other@example.com"])
          adapter (success-adapter)
          client  (create-client-with adapter)]
      (emails/send! client params)
      (let [body (json/parse-string (:body (mock/last-call adapter)) true)]
        (is (contains? body :reply_to))
        (is (not (contains? body :reply-to)))))))

(deftest send-includes-auth-header
  (testing "Authorization header is set with Bearer token"
    (let [adapter (success-adapter)
          client  (create-client-with adapter)]
      (emails/send! client valid-params)
      (is (= "Bearer re_test_key"
             (get-in (mock/last-call adapter) [:headers "Authorization"]))))))

(deftest send-returns-error-envelope-on-failure
  (testing "API error returns {:data nil :error {...}} without throwing"
    (let [adapter (error-adapter)
          client  (create-client-with adapter)
          result  (emails/send! client valid-params)]
      (is (nil? (:data result)))
      (is (some? (:error result)))
      (is (= "missing_api_key" (get-in result [:error :name]))))))

(deftest send-omits-nil-fields
  (testing "nil optional fields are not included in the request body"
    (let [params  (assoc valid-params :bcc nil :cc nil)
          adapter (success-adapter)
          client  (create-client-with adapter)]
      (emails/send! client params)
      (let [body (json/parse-string (:body (mock/last-call adapter)) true)]
        (is (not (contains? body :bcc)))
        (is (not (contains? body :cc)))))))

;; ---------------------------------------------------------------------------
;; emails/send! – validation
;; ---------------------------------------------------------------------------

(deftest send-throws-on-missing-from
  (testing "missing :from raises ex-info"
    (let [client (create-client-with (success-adapter))]
      (is (thrown-with-msg? Exception #"Missing required field: :from"
                            (emails/send! client (dissoc valid-params :from)))))))

(deftest send-throws-on-missing-subject
  (testing "missing :subject raises ex-info"
    (let [client (create-client-with (success-adapter))]
      (is (thrown-with-msg? Exception #"Missing required field: :subject"
                            (emails/send! client (dissoc valid-params :subject)))))))

(deftest send-throws-on-missing-to
  (testing "missing :to raises ex-info"
    (let [client (create-client-with (success-adapter))]
      (is (thrown-with-msg? Exception #"Missing required field: :to"
                            (emails/send! client (dissoc valid-params :to)))))))

;; ---------------------------------------------------------------------------
;; emails/send-batch!
;; ---------------------------------------------------------------------------

(deftest send-batch-uses-batch-url
  (testing "send-batch targets POST /emails/batch"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200
                     :body   {:data [{:id "id-1"} {:id "id-2"}]}}])
          client  (create-client-with adapter)]
      (emails/send-batch! client [valid-params valid-params])
      (is (= (str base-url "/emails/batch")
             (:url (mock/last-call adapter)))))))

(deftest send-batch-sends-all-emails
  (testing "send-batch serialises all emails in a JSON array"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200
                     :body   {:data [{:id "id-1"} {:id "id-2"}]}}])
          client  (create-client-with adapter)
          batch   [valid-params valid-params]]
      (emails/send-batch! client batch)
      (let [body (json/parse-string (:body (mock/last-call adapter)) true)]
        (is (= 2 (count body)))))))

;; ---------------------------------------------------------------------------
;; emails/get-email!
;; ---------------------------------------------------------------------------

(deftest get-email-uses-get-method
  (testing "get-email issues a GET request"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200
                     :body   {:id      "abc"
                              :from    "a@b.com"
                              :subject "Hello"}}])
          client  (create-client-with adapter)]
      (emails/get-email! client "abc")
      (is (= :get (:method (mock/last-call adapter)))))))

(deftest get-email-hits-correct-url
  (testing "get-email targets GET /emails/:id"
    (let [email-id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:id email-id}}])
          client   (create-client-with adapter)]
      (emails/get-email! client email-id)
      (is (= (str base-url "/emails/" email-id)
             (:url (mock/last-call adapter)))))))

(deftest get-email-converts-response-keys
  (testing "response keys are converted to kebab-case"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200
                     :body   {:id         "abc"
                              :created_at "2024-01-01T00:00:00Z"}}])
          client  (create-client-with adapter)
          result  (emails/get-email! client "abc")]
      (is (contains? (:data result) :created-at))
      (is (not (contains? (:data result) :created_at))))))

;; ---------------------------------------------------------------------------
;; emails/update-email!
;; ---------------------------------------------------------------------------

(deftest update-email-uses-patch-method
  (testing "update-email issues a PATCH request"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:id "abc"}}])
          client  (create-client-with adapter)]
      (emails/update-email! client "abc" {:scheduled-at "2024-12-25T09:00:00Z"})
      (is (= :patch (:method (mock/last-call adapter)))))))

;; ---------------------------------------------------------------------------
;; emails/cancel-email!
;; ---------------------------------------------------------------------------

(deftest cancel-email-uses-correct-url
  (testing "cancel-email targets POST /emails/:id/cancel"
    (let [email-id "abc"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "email" :id email-id}}])
          client   (create-client-with adapter)]
      (emails/cancel-email! client email-id)
      (is (= (str base-url "/emails/" email-id "/cancel")
             (:url (mock/last-call adapter)))))))