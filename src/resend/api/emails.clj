(ns resend.api.emails
  "Emails resource – wraps the /emails Resend REST endpoints.
 
  Mirrors the interface of the official SDKs (Node, Python, Java, Go, etc.):
 
    emails/send!             POST   /emails
    emails/send-batch!       POST   /emails/batch
    emails/get-email!        GET    /emails/:id
    emails/update-email!     PATCH  /emails/:id
    emails/cancel-email!     POST   /emails/:id/cancel
    emails/list-emails!      GET    /emails
    emails/share-email!      POST   /emails/:id/share
    emails/get-attachment!   GET    /emails/:email_id/attachments/:id
    emails/list-attachments! GET    /emails/:email_id/attachments
    emails/get-metrics!      GET    /emails/metrics
 
  All functions return {:data <parsed-map> :error nil} on success or
  {:data nil :error <error-map>} on failure, so callers never need a
  try/catch for normal API errors.
 
  References:
  - Resend send email API: https://resend.com/docs/api-reference/emails/send-email"
  (:require [resend.internal.util :as util]
            [resend.internal.request :as request]))

(defn- validate-send-params!
  "Throws an ex-info if required fields are missing."
  [{:keys [from to subject]}]
  (when-not from
    (throw (ex-info "Missing required field: :from" {:field :from})))
  (when-not (or (string? to) (sequential? to))
    (throw (ex-info "Missing required field: :to" {:field :to})))
  (when-not subject
    (throw (ex-info "Missing required field: :subject" {:field :subject}))))

(defn send!
  "Sends a single email.
 
  `client`  – ResendClient created with `resend.internal.client/create-client`
  `params`  – map with the following keys:
 
    Required:
      :from        string  – Sender email address
      :to          string | [string]  – Recipient(s)
      :subject     string  – Email subject
 
    Optional (all kebab-case; converted to snake_case automatically):
      :bcc         string | [string]  – BCC recipient(s)
      :cc          string | [string]  – CC recipient(s)
      :reply-to    string | [string]  – Reply-to address(es)
      :html        string  – HTML body
      :text        string  – Plain-text body
      :headers     [{:name string :value string}] – Custom headers
      :attachments [{:filename string :content string}] – Base64 attachments
      :tags        [{:name string :value string}] – Custom tags for analytics
      :scheduled-at string – ISO-8601 datetime to schedule delivery
 
  Returns {:data {:id \"<email-id>\"} :error nil} on success.
  Returns {:data nil :error {:name \"...\" :message \"...\"}} on failure.
 
  Example:
    (emails/send client
      {:from    \"Acme <no-reply@acme.com>\"
       :to      [\"user@example.com\"]
       :subject \"Welcome!\"
       :html    \"<strong>Hello!</strong>\"
       :tags    [{:name \"category\" :value \"welcome\"}]})"
  [client params]
  (validate-send-params! params)
  (let [payload (-> params
                    util/remove-nils
                    util/clj->api)]
    (request/do-request client :post "/emails" payload)))

(defn send-batch!
  "Sends up to 100 emails in a single API call.
 
  `client`  – ResendClient
  `emails`  – sequence of param maps; each has the same shape as `send`
 
  Returns {:data {:data [{:id \"...\"}]} :error nil} on success.
 
  Example:
    (emails/send-batch client
      [{:from \"a@x.com\" :to \"b@y.com\" :subject \"Hi\" :text \"Hello\"}
       {:from \"a@x.com\" :to \"c@y.com\" :subject \"Hi\" :text \"Hello\"}])"
  [client emails]
  {:pre [(sequential? emails) (pos? (count emails)) (<= (count emails) 100)]}
  (let [payload (mapv (comp util/clj->api util/remove-nils) emails)]
    (request/do-request client :post "/emails/batch" payload)))

(defn get-email!
  "Retrieves a previously sent email by its ID.
 
  Returns {:data {:id ... :from ... :to ... :subject ... :created-at ...} :error nil}.
 
  Example:
    (emails/get-email client \"49a3999c-0ce1-4ea6-ab68-e08835cf401e\")"
  [client email-id]
  {:pre [(string? email-id) (seq email-id)]}
  (request/do-request client :get (str "/emails/" email-id) nil))

(defn update-email!
  "Updates a scheduled email before it is sent.
 
  `params` accepted keys:
    :scheduled-at  string  – new ISO-8601 scheduled time
 
  Example:
    (emails/update-email client \"49a3999c-...\" {:scheduled-at \"2024-12-01T09:00:00Z\"})"
  [client email-id params]
  {:pre [(string? email-id) (seq email-id)]}
  (let [payload (-> params util/remove-nils util/clj->api)]
    (request/do-request client :patch (str "/emails/" email-id) payload)))

(defn cancel-email!
  "Cancels a scheduled email (must not have been sent yet).
 
  Example:
    (emails/cancel-email client \"49a3999c-...\")"
  [client email-id]
  {:pre [(string? email-id) (seq email-id)]}
  (request/do-request client :post (str "/emails/" email-id "/cancel") nil))

(defn list-emails!
  "Retrieves a paginated list of emails sent by your team.
 
  `params` (optional) – map with:
    :limit   number  – Number of emails to retrieve (1–100, default 20)
    :after   string  – Cursor: return emails after this ID
    :before  string  – Cursor: return emails before this ID
 
  Returns {:data {:object \"list\" :has-more bool :data [...]} :error nil}.
 
  Example:
    (emails/list-emails! client {:limit 10})"
  ([client] (list-emails! client nil))
  ([client params]
   (request/do-request client :get (str "/emails" (util/build-query-string params)) nil)))

(defn share-email!
  "Creates a shareable link to view a sent email.
 
  `email-id` – The email ID.
  `params`   (optional) – map with:
    :expires-in  string  – Duration the link stays valid (e.g. \"48h\", \"2 hours\", \"1 day\").
                           Defaults to \"48h\"; cannot exceed 48 hours.
 
  Returns {:data {:id \"...\" :url \"https://resend.com/shared?token=...\"} :error nil}.
 
  Example:
    (emails/share-email! client \"49a3999c-...\" {:expires-in \"2 hours\"})"
  ([client email-id] (share-email! client email-id nil))
  ([client email-id params]
   {:pre [(string? email-id) (seq email-id)]}
   (let [payload (some-> params util/remove-nils util/clj->api (not-empty))]
     (request/do-request client :post (str "/emails/" email-id "/share") payload))))

(defn get-attachment!
  "Retrieves a single attachment from a sent email.
 
  `email-id`      – The email ID.
  `attachment-id` – The attachment ID.
 
  Returns {:data {:id \"...\" :filename \"...\" :download-url \"...\" ...} :error nil}.
 
  Example:
    (emails/get-attachment! client
      \"4ef9a417-02e9-4d39-ad75-9611e0fcc33c\"
      \"2a0c9ce0-3112-4728-976e-47ddcd16a318\")"
  [client email-id attachment-id]
  {:pre [(string? email-id) (seq email-id)
         (string? attachment-id) (seq attachment-id)]}
  (request/do-request client :get (str "/emails/" email-id "/attachments/" attachment-id) nil))

(defn list-attachments!
  "Retrieves a list of attachments from a sent email.
 
  `email-id` – The email ID.
  `params`   (optional) – map with:
    :limit   number  – Number of attachments to retrieve (1–100)
    :after   string  – Cursor: return attachments after this ID
    :before  string  – Cursor: return attachments before this ID
 
  Returns {:data {:object \"list\" :has-more bool :data [...]} :error nil}.
 
  Example:
    (emails/list-attachments! client \"4ef9a417-02e9-4d39-ad75-9611e0fcc33c\")"
  ([client email-id] (list-attachments! client email-id nil))
  ([client email-id params]
   {:pre [(string? email-id) (seq email-id)]}
   (request/do-request client :get (str "/emails/" email-id "/attachments" (util/build-query-string params)) nil)))

(defn get-metrics!
  "Retrieves account-level email metrics.
 
  `params` (optional) – map with:
    :start-date   string    – Start of date range (ISO 8601, e.g. \"2026-07-01\")
    :end-date     string    – End of date range (ISO 8601). Defaults to now.
    :timezone     string    – IANA timezone (e.g. \"America/New_York\"), default \"UTC\"
    :granularity  string    – Bucket size: \"hourly\" | \"daily\" | \"weekly\" | \"monthly\", default \"daily\"
    :metrics      [string]  – Metrics to include (e.g. [\"sent\" \"delivered\" \"open_rate\"])
    :dimensions   [string]  – Dimensions to break down by (e.g. [\"period\" \"domain\"])
    :domain-id    [string]  – Sending domain IDs to filter by (up to 100)
    :email-id     [string]  – Email IDs to filter by (up to 100)
    :broadcast-id [string]  – Broadcast IDs to filter by (up to 100)
 
  Returns {:data {:object \"metrics\" :totals {...} :data [...]} :error nil}.
 
  Example:
    (emails/get-metrics! client
      {:start-date  \"2026-07-01\"
       :end-date    \"2026-07-08\"
       :metrics     [\"sent\" \"delivered\" \"open_rate\"]
       :dimensions  [\"period\"]})"
  ([client] (get-metrics! client nil))
  ([client params]
   (request/do-request client :get (str "/emails/metrics" (util/build-query-string params)) nil)))