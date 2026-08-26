(ns resend-clojure.internal.response
  "Converts raw HTTP responses into the {:data … :error …} envelope used by the SDK.
  Success: 2xx  → {:data <kebab-case map>  :error nil}
  Failure: 4xx+ → {:data nil :error <kebab-case map>}"
  (:require [resend-clojure.internal.util :as util]))

(defn- success?
  "Returns true if the HTTP status code indicates success (2xx)."
  [status]
  (and (>= status 200) (<= status 299)))

(defn handle-response
  "Converts a raw HTTP response into the {:data … :error …} envelope.
  Success: 2xx  → {:data <kebab-case map>  :error nil}
  Failure: 4xx+ → {:data nil :error <kebab-case map>}"
  [{:keys [status body]}]
  (if (success? status)
    {:data  (util/api->clj body) :error nil}
    {:data  nil :error (util/api->clj body)}))