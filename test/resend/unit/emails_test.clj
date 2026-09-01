(ns resend.unit.emails-test
  "Unit tests for resend.emails.

  All tests use MockAdapter – no real HTTP calls are made.

  References:
  - Resend email API: https://resend.com/docs/api-reference/emails/send-email"
  (:require [clojure.test :refer [deftest testing is]]
            [cheshire.core :as json]
            [resend.api.emails :as emails]
            [resend.internal.client :as client]
            [resend.internal.http-mock-adapter :as mock]))

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

;; ---------------------------------------------------------------------------
;; emails/list-emails!
;; ---------------------------------------------------------------------------

(deftest list-emails-uses-get-method
  (testing "list-emails issues a GET request"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "list" :has_more false :data []}}])
          client  (create-client-with adapter)]
      (emails/list-emails! client)
      (is (= :get (:method (mock/last-call adapter)))))))

(deftest list-emails-hits-correct-url-no-params
  (testing "list-emails without params targets GET /emails"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "list" :has_more false :data []}}])
          client  (create-client-with adapter)]
      (emails/list-emails! client)
      (is (= (str base-url "/emails")
             (:url (mock/last-call adapter)))))))

(deftest list-emails-appends-query-params
  (testing "list-emails appends limit as query param"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "list" :has_more false :data []}}])
          client  (create-client-with adapter)]
      (emails/list-emails! client {:limit 10})
      (is (= (str base-url "/emails?limit=10")
             (:url (mock/last-call adapter)))))))

(deftest list-emails-returns-data-envelope
  (testing "list-emails returns success envelope with list data"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200
                     :body   {:object "list" :has_more false
                              :data   [{:id "id-1" :subject "Hi"}]}}])
          client  (create-client-with adapter)
          result  (emails/list-emails! client)]
      (is (nil? (:error result)))
      (is (= "list" (get-in result [:data :object]))))))

;; ---------------------------------------------------------------------------
;; emails/share-email!
;; ---------------------------------------------------------------------------

(deftest share-email-uses-post-method
  (testing "share-email issues a POST request"
    (let [email-id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "email" :id email-id
                                         :url "https://resend.com/shared?token=abc"}}])
          client   (create-client-with adapter)]
      (emails/share-email! client email-id)
      (is (= :post (:method (mock/last-call adapter)))))))

(deftest share-email-hits-correct-url
  (testing "share-email targets POST /emails/:id/share"
    (let [email-id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "email" :id email-id :url "https://resend.com/shared?token=abc"}}])
          client   (create-client-with adapter)]
      (emails/share-email! client email-id)
      (is (= (str base-url "/emails/" email-id "/share")
             (:url (mock/last-call adapter)))))))

(deftest share-email-sends-expires-in-as-snake-case
  (testing "share-email serialises :expires-in as expires_in"
    (let [email-id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "email" :id email-id :url "https://resend.com/shared?token=abc"}}])
          client   (create-client-with adapter)]
      (emails/share-email! client email-id {:expires-in "2 hours"})
      (let [body (json/parse-string (:body (mock/last-call adapter)) true)]
        (is (= "2 hours" (:expires_in body)))))))

(deftest share-email-sends-no-body-when-no-params
  (testing "share-email sends no body when params are omitted"
    (let [email-id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "email" :id email-id :url "https://resend.com/shared?token=abc"}}])
          client   (create-client-with adapter)]
      (emails/share-email! client email-id)
      (is (nil? (:body (mock/last-call adapter)))))))

(deftest share-email-returns-url
  (testing "share-email returns a shareable URL in :data"
    (let [email-id "49a3999c-0ce1-4ea6-ab68-e08835cf401e"
          share-url "https://resend.com/shared?token=abc123"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "email" :id email-id :url share-url}}])
          client   (create-client-with adapter)
          result   (emails/share-email! client email-id)]
      (is (nil? (:error result)))
      (is (= share-url (get-in result [:data :url]))))))

;; ---------------------------------------------------------------------------
;; emails/get-attachment!
;; ---------------------------------------------------------------------------

(deftest get-attachment-uses-get-method
  (testing "get-attachment issues a GET request"
    (let [email-id      "4ef9a417-02e9-4d39-ad75-9611e0fcc33c"
          attachment-id "2a0c9ce0-3112-4728-976e-47ddcd16a318"
          adapter       (mock/make-mock-adapter
                         [{:status 200 :body {:object "attachment" :id attachment-id
                                              :filename "avatar.png"}}])
          client        (create-client-with adapter)]
      (emails/get-attachment! client email-id attachment-id)
      (is (= :get (:method (mock/last-call adapter)))))))

(deftest get-attachment-hits-correct-url
  (testing "get-attachment targets GET /emails/:email_id/attachments/:id"
    (let [email-id      "4ef9a417-02e9-4d39-ad75-9611e0fcc33c"
          attachment-id "2a0c9ce0-3112-4728-976e-47ddcd16a318"
          adapter       (mock/make-mock-adapter
                         [{:status 200 :body {:object "attachment" :id attachment-id
                                              :filename "avatar.png"}}])
          client        (create-client-with adapter)]
      (emails/get-attachment! client email-id attachment-id)
      (is (= (str base-url "/emails/" email-id "/attachments/" attachment-id)
             (:url (mock/last-call adapter)))))))

(deftest get-attachment-returns-data-envelope
  (testing "get-attachment returns attachment data in :data key"
    (let [email-id      "4ef9a417-02e9-4d39-ad75-9611e0fcc33c"
          attachment-id "2a0c9ce0-3112-4728-976e-47ddcd16a318"
          adapter       (mock/make-mock-adapter
                         [{:status 200
                           :body   {:object       "attachment"
                                    :id           attachment-id
                                    :filename     "avatar.png"
                                    :content_type "image/png"}}])
          client        (create-client-with adapter)
          result        (emails/get-attachment! client email-id attachment-id)]
      (is (nil? (:error result)))
      (is (= attachment-id (get-in result [:data :id])))
      (is (= "avatar.png" (get-in result [:data :filename]))))))

;; ---------------------------------------------------------------------------
;; emails/list-attachments!
;; ---------------------------------------------------------------------------

(deftest list-attachments-uses-get-method
  (testing "list-attachments issues a GET request"
    (let [email-id "4ef9a417-02e9-4d39-ad75-9611e0fcc33c"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "list" :has_more false :data []}}])
          client   (create-client-with adapter)]
      (emails/list-attachments! client email-id)
      (is (= :get (:method (mock/last-call adapter)))))))

(deftest list-attachments-hits-correct-url
  (testing "list-attachments targets GET /emails/:email_id/attachments"
    (let [email-id "4ef9a417-02e9-4d39-ad75-9611e0fcc33c"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "list" :has_more false :data []}}])
          client   (create-client-with adapter)]
      (emails/list-attachments! client email-id)
      (is (= (str base-url "/emails/" email-id "/attachments")
             (:url (mock/last-call adapter)))))))

(deftest list-attachments-appends-query-params
  (testing "list-attachments appends limit as query param"
    (let [email-id "4ef9a417-02e9-4d39-ad75-9611e0fcc33c"
          adapter  (mock/make-mock-adapter
                    [{:status 200 :body {:object "list" :has_more false :data []}}])
          client   (create-client-with adapter)]
      (emails/list-attachments! client email-id {:limit 5})
      (is (= (str base-url "/emails/" email-id "/attachments?limit=5")
             (:url (mock/last-call adapter)))))))

;; ---------------------------------------------------------------------------
;; emails/get-metrics!
;; ---------------------------------------------------------------------------

(deftest get-metrics-uses-get-method
  (testing "get-metrics issues a GET request"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "metrics" :totals {:sent 100}}}])
          client  (create-client-with adapter)]
      (emails/get-metrics! client)
      (is (= :get (:method (mock/last-call adapter)))))))

(deftest get-metrics-hits-correct-url-no-params
  (testing "get-metrics without params targets GET /emails/metrics"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "metrics" :totals {:sent 100}}}])
          client  (create-client-with adapter)]
      (emails/get-metrics! client)
      (is (= (str base-url "/emails/metrics")
             (:url (mock/last-call adapter)))))))

(deftest get-metrics-appends-scalar-query-params
  (testing "get-metrics appends scalar params like start_date"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "metrics" :totals {:sent 100}}}])
          client  (create-client-with adapter)]
      (emails/get-metrics! client {:start-date "2026-07-01"})
      (let [url (:url (mock/last-call adapter))]
        (is (clojure.string/includes? url "start_date=2026-07-01"))))))

(deftest get-metrics-serialises-array-params-as-repeated
  (testing "get-metrics repeats array params (metrics=sent&metrics=delivered)"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200 :body {:object "metrics" :totals {:sent 100 :delivered 90}}}])
          client  (create-client-with adapter)]
      (emails/get-metrics! client {:metrics ["sent" "delivered"]})
      (let [url (:url (mock/last-call adapter))]
        (is (clojure.string/includes? url "metrics=sent"))
        (is (clojure.string/includes? url "metrics=delivered"))))))

(deftest get-metrics-returns-data-envelope
  (testing "get-metrics returns metrics data in :data key"
    (let [adapter (mock/make-mock-adapter
                   [{:status 200
                     :body   {:object "metrics"
                              :totals {:sent 1204 :delivered 1180}
                              :data   []}}])
          client  (create-client-with adapter)
          result  (emails/get-metrics! client)]
      (is (nil? (:error result)))
      (is (= "metrics" (get-in result [:data :object])))
      (is (= 1204 (get-in result [:data :totals :sent]))))))