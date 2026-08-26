(ns resend-clojure.internal.http-mock-adapter
  "A MockAdapter that avoids real HTTP calls in unit tests.
 
  Pattern: record captures the canned responses you want to return,
  plus a `calls` atom that logs every request for assertion."
  (:require [resend-clojure.internal.http :as http]))

(defrecord MockAdapter
  ;; responses – vector of {:status int :body map} returned in order (cycles)
  ;; calls     – atom holding a vector of every request opts map received
           [responses calls]

  http/IHttpAdapter
  (request [this opts]
    (swap! (:calls this) conj opts)
    (let [idx      (mod (dec (count @(:calls this)))
                        (count (:responses this)))
          response (nth (:responses this) idx)]
      response)))

(defn make-mock-adapter
  "Creates a MockAdapter that returns `responses` in round-robin order.
 
  Example:
    (make-mock-adapter [{:status 200 :body {:id \"abc123\"}}])"
  [responses]
  (->MockAdapter responses (atom [])))

(defn last-call
  "Returns the most recent request map recorded by the adapter."
  [adapter]
  (last @(:calls adapter)))

(defn all-calls
  "Returns all recorded request maps."
  [adapter]
  @(:calls adapter))

(defn call-count
  "Returns how many requests were made."
  [adapter]
  (count @(:calls adapter)))