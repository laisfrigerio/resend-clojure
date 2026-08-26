(ns resend-clojure.api.emails
  "Emails resource – wraps the /emails Resend REST endpoints.
 
  Mirrors the interface of the official SDKs (Node, Python, Java, Go, etc.):
 
    emails/send!        POST   /emails
    emails/send-batch!  POST   /emails/batch
    emails/get!         GET    /emails/:id
    emails/update!      PATCH  /emails/:id
    emails/cancel!      POST   /emails/:id/cancel
 
  All functions return {:data <parsed-map> :error nil} on success or
  {:data nil :error <error-map>} on failure, so callers never need a
  try/catch for normal API errors. Bang (!) variants throw instead.
 
  References:
  - Resend send email API: https://resend.com/docs/api-reference/emails/send-email"
  (:require [resend-clojure.internal.util :as util]
            [resend-clojure.internal.request :as request]))

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
 
  `client`  – ResendClient created with `resend-clojure.internal.client/create-client`
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