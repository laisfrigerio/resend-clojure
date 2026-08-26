(ns resend-clojure.internal.util
  (:require [clojure.walk :as walk]
            [clojure.string :as str]))

(defn- kebab->snake [k]
  (if (keyword? k)
    (keyword (str/replace (name k) "-" "_"))
    k))

(defn- snake->kebab [k]
  (if (keyword? k)
    (keyword (str/replace (name k) "_" "-"))
    k))

(defn- transform-keys [f m]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       (into {} (map (fn [[k v]] [(f k) v]) x))
       x))
   m))

(defn remove-nils
  "Removes nil-valued entries from a map (non-recursive).
  Useful before serialising a payload so optional fields are omitted."
  [m]
  (reduce-kv (fn [acc k v]
               (if (nil? v) acc (assoc acc k v)))
             {}
             m))

(defn api->clj
  "Recursively converts an API response map with snake_case string keys into
  a Clojure map with kebab-case keyword keys.
 
  Example:
    (api->clj {\"message_id\" \"abc\" \"created_at\" \"2024-01-01\"})
    ;; => {:message-id \"abc\" :created-at \"2024-01-01\"}"
  [body]
  (->> body
       (transform-keys snake->kebab)))

(defn clj->api
  "Recursively converts a Clojure map with kebab-case keyword keys into a
  map with snake_case string keys ready for JSON serialisation.
 
  Example:
    (clj->api {:from \"a@b.com\" :reply-to [\"c@d.com\"]})
    ;; => {\"from\" \"a@b.com\" \"reply_to\" [\"c@d.com\"]}"
  [body]
  (->> body
       (transform-keys kebab->snake)))