# Resend Clojure SDK

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Clojars Version](https://img.shields.io/clojars/v/net.clojars.laisfrigerio/resend-clojure.svg)](https://clojars.org/net.clojars.laisfrigerio/resend-clojure)
[![CI](https://github.com/laisfrigerio/resend-clojure/actions/workflows/ci.yml/badge.svg)](https://github.com/laisfrigerio/resend-clojure/actions)

Clojure library for the [Resend](https://resend.com) API.

## Installation

### deps.edn

Add the following dependency to your `deps.edn`:

```clojure
net.clojars.laisfrigerio/resend-clojure {:mvn/version "0.1.0"}
```

### Leiningen / Boot

Or add the following dependency to your `project.clj`:

```clojure
[net.clojars.laisfrigerio/resend-clojure "0.1.0"]
```

## Setup

First, you need to get an API key, which is available in the [Resend Dashboard](https://resend.com/api-keys).

```clojure
(require '[resend.core :as resend])

(def client (resend/create-client "re_xxxx...xxxxxx"))
```

You can also configure the client via an environment variable:

```clojure
;; Reads RESEND_API_KEY from the environment automatically
(def client (resend/create-client))
```

## Usage

### Emails

#### Send an email

```clojure
(require '[resend.emails :as emails])

(emails/send! client
(require '[resend.core :as resend]
         '[resend.emails :as emails])

(def client (resend/create-client "re_xxxx...xxxxxx"))

(let [{:keys [data error]} (emails/send! client
                             {:from    "you@example.com"
                              :to      ["user@gmail.com"]
                              :subject "Hello, world!"
                              :text    "It works!"})]
  (if error
    (println "Error:" (:message error))
    (println "Email sent:" (:id data))))
```

> Note: In order to send from your own domain, you will first need to verify your domain in the Resend Dashboard.

#### Send email using HTML

```clojure
(let [{:keys [data error]} (emails/send! client
                             {:from    "you@example.com"
                              :to      ["user@gmail.com"]
                              :subject "Hello, world!"
                              :html    "<strong>It works!</strong>"})]
  (if error
    (println "Error:" (:message error))
    (println "Email sent with HTML:" (:id data))))
```

#### Send email with CC, BCC and Reply-To

```clojure
(emails/send! client
  {:from     "you@example.com"
   :to       ["user@gmail.com"]
   :cc       ["cc@example.com"]
   :bcc      ["bcc@example.com"]
   :reply-to "reply@example.com"
   :subject  "Hello, world!"
   :html     "<strong>It works!</strong>"})
```

#### Send email with attachments

```clj
(emails/send! client
  {:from        "you@example.com"
   :to          ["user@gmail.com"]
   :subject     "Invoice attached"
   :html        "<p>Please find your invoice attached.</p>"
   :attachments [{:filename "invoice.pdf"
                  :content  (java.util.Base64/getEncoder
                              (.encodeToString (slurp "invoice.pdf")))}]})
```

#### Send email with tags

```clj
(emails/send! client
  {:from    "you@example.com"
   :to      ["user@gmail.com"]
   :subject "Hello, world!"
   :html    "<strong>It works!</strong>"
   :tags    [{:name "category" :value "welcome"}
             {:name "user-id"  :value "12345"}]})
```

#### Send a batch of emails

```clojure
(emails/send-batch! client
  [{:from    "you@example.com"
    :to      ["user1@gmail.com"]
    :subject "Welcome, Alice!"
    :html    "<p>Hello Alice!</p>"}
   {:from    "you@example.com"
    :to      ["user2@gmail.com"]
    :subject "Welcome, Bob!"
    :html    "<p>Hello Bob!</p>"}])
```

#### Retrieve an email

```clojure
(require '[resend.emails :as emails])

(let [{:keys [data error]} (emails/get client "email-id-here")]
  (when data
    (println "From:"    (:from data))
    (println "To:"      (:to data))
    (println "Subject:" (:subject data))))
```

#### Update a scheduled email

```clojure
(emails/update! client "email-id-here"
  {:scheduled-at "2024-12-31T10:00:00.000Z"})
```

#### Cancel a scheduled email

```clojure
(emails/cancel! client "email-id-here")
```

## Error Handling

All functions return a map with `:data` and `:error` keys. By default, no exceptions are thrown:

```clj
(let [{:keys [data error]} (emails/send! client params)]
  (if error
    ;; error is a map: {:message "..." :name "..." :status-code 422}
    (handle-error error)
    ;; data is a map with the API response
    (handle-success data)))
```

If you prefer an exception-based style, use the `!`-suffixed throwing variants:

```clj
;; Throws an ExceptionInfo on API errors
(try
  (let [data (emails/send!! client params)]
    (println "Sent:" (:id data)))
  (catch clojure.lang.ExceptionInfo e
    (println "API error:" (ex-message e))
    (println "Details:"   (ex-data e))))
```

## API Reference

### Emails
 
| Function | Description |
|---|---|
| `emails/send!` | Send an email |
| `emails/send-batch!` | Send a batch of up to 100 emails |
| `emails/get` | Retrieve a sent email by ID |
| `emails/update!` | Update a scheduled email |
| `emails/cancel!` | Cancel a scheduled email |

### Contacts

```clojure
(require '[resend.contacts :as contacts])
 
;; Create a contact
(contacts/create! client "audience-id" {:email "user@example.com" :first-name "Alice"})
 
;; List contacts in an audience
(contacts/list client "audience-id")
 
;; Get a contact
(contacts/get client "audience-id" "contact-id")
 
;; Update a contact
(contacts/update! client "audience-id" "contact-id" {:first-name "Alicia"})
 
;; Remove a contact
(contacts/delete! client "audience-id" "contact-id")
```

### Audiences
 
```clojure
(require '[resend.audiences :as audiences])
 
;; Create an audience
(audiences/create! client {:name "Newsletter Subscribers"})
 
;; List all audiences
(audiences/list client)
 
;; Get an audience
(audiences/get client "audience-id")
 
;; Delete an audience
(audiences/delete! client "audience-id")
```

### Broadcasts
 
```clojure
(require '[resend.broadcasts :as broadcasts])
 
;; Create a broadcast
(broadcasts/create! client
  {:audience-id "audience-id"
   :from        "you@example.com"
   :subject     "Monthly Newsletter"
   :html        "<p>What's new this month...</p>"})
 
;; Send a broadcast
(broadcasts/send! client "broadcast-id")
 
;; List all broadcasts
(broadcasts/list client)
 
;; Get a broadcast
(broadcasts/get client "broadcast-id")
 
;; Delete a broadcast
(broadcasts/delete! client "broadcast-id")
```

### Domains
 
```clojure
(require '[resend.domains :as domains])
 
;; Create a domain
(domains/create! client {:name "example.com"})
 
;; List all domains
(domains/list client)
 
;; Get a domain
(domains/get client "domain-id")
 
;; Verify a domain
(domains/verify! client "domain-id")
 
;; Update a domain
(domains/update! client "domain-id" {:click-tracking true :open-tracking true})
 
;; Delete a domain
(domains/delete! client "domain-id")
```

### API Keys
 
```clojure
(require '[resend.api-keys :as api-keys])
 
;; Create an API key
(api-keys/create! client {:name "Production Key" :permission "full_access"})
 
;; List all API keys
(api-keys/list client)
 
;; Delete an API key
(api-keys/delete! client "api-key-id")
```

### Webhooks
 
```clojure
(require '[resend.webhooks :as webhooks])
 
;; Create a webhook
(webhooks/create! client
  {:endpoint "https://yourapp.com/webhooks/resend"
   :events   ["email.sent" "email.delivered" "email.bounced"]})
 
;; List all webhooks
(webhooks/list client)
 
;; Get a webhook
(webhooks/get client "webhook-id")
 
;; Delete a webhook
(webhooks/delete! client "webhook-id")
```

## Advanced Configuration

### Custom HTTP Adapter

`resend-clojure` uses [hato](https://github.com/gnarroway/hato) as the default HTTP client. You can plug in your own adapter by implementing the `HttpAdapter` protocol:
 
```clojure
(require '[resend.http :refer [HttpAdapter]])
 
(defrecord MyHttpAdapter []
  HttpAdapter
  (request [_ opts]
    ;; implement using your preferred HTTP client
    ))
 
(def client (resend/create-client "re_xxxx...xxxxxx"
              {:http-adapter (->MyHttpAdapter)}))
```
 
### Timeout and Retry Configuration
 
```clojure
(def client (resend/create-client "re_xxxx...xxxxxx"
              {:timeout-ms      5000
               :max-retries     3
               :retry-on        #{429 500 502 503 504}
               :base-backoff-ms 200}))
```

## Support

- [Resend Documentation](https://resend.com/docs/introduction)
- [Resend API Reference](https://resend.com/docs/api-reference/introduction)
- [GitHub Issues](https://github.com/laisfrigerio/resend-clojure/issues)

## Contributing
 
Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute to this project.


## Development Setup

### Prerequisites

#### Java

Leiningen requires Java 8+. Install via [SDKMAN](https://sdkman.io) to manage java versions (recommended):

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java
```

Or via Homebrew (macOS):

```bash
brew install openjdk
```

#### Leiningen

```bash
brew install leiningen
```

Or manually:

```bash
curl -o ~/bin/lein https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
chmod +x ~/bin/lein
lein # downloads itself on first run
```

Verify installation:

```bash
lein -v
```

### Clone and install dependencies

```bash
git clone https://github.com/laisfrigerio/resend-clojure.git
cd resend-clojure
lein deps
```

### Git hooks (lint + tests on every commit)

After cloning, run once to activate the pre-commit hook:

```bash
bash scripts/setup-hooks.sh
```

This configures Git to run automatically on every `git commit`:

1. `lein lint-fix` — formats the code
2. `lein test` — runs the test suite; if any test fails, the commit is aborted

### Running tests manually

```bash
lein test
```

### Running lint manually

```bash
lein lint       # check only
lein lint-fix   # check and auto-fix
```

## License

MIT License — see [LICENSE](LICENSE) for details.
