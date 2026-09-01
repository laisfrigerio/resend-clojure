;; Script interativo para exercitar o namespace resend.api.emails
;; Execução: bb email_api.clj
;;
;; Requer bb.edn com:
;;   {:deps {resend-clojure/resend-clojure {:local/root "../"}}}

(require '[resend.core :as resend]
         '[resend.api.emails :as emails]
         '[clojure.pprint :as pp])

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(def client (resend/create-client))

(defn prompt [label]
  (print (str label ": "))
  (flush)
  (read-line))

(defn prompt-optional [label]
  (let [v (prompt (str label " (Enter para pular)"))]
    (when (seq v) v)))

(defn print-result [result]
  (println)
  (pp/pprint result)
  (println))

;; ---------------------------------------------------------------------------
;; Ações do menu
;; ---------------------------------------------------------------------------

(defn split-csv [s]
  (mapv clojure.string/trim (clojure.string/split s #",")))

(defn parse-name-value-pairs
  "Converte 'name1=val1,name2=val2' em [{:name 'name1' :value 'val1'} ...]"
  [s]
  (->> (clojure.string/split s #",")
       (mapv clojure.string/trim)
       (mapv (fn [pair]
               (let [[n v] (clojure.string/split pair #"=" 2)]
                 {:name (clojure.string/trim n)
                  :value (clojure.string/trim (or v ""))})))))

(defn file->base64 [path]
  (.encodeToString (java.util.Base64/getEncoder)
                   (java.nio.file.Files/readAllBytes
                     (.toPath (java.io.File. path)))))

(defn prompt-attachments []
  (when-let [paths (prompt-optional "attachments (caminhos separados por vírgula)")]
    (->> (split-csv paths)
         (mapv (fn [path]
                 {:filename (.getName (java.io.File. path))
                  :content  (file->base64 path)})))))

(defn action-send! []
  (println "\n--- Enviar e-mail ---")
  (let [from         (prompt "from")
        to           (prompt "to (vírgula para múltiplos)")
        subject      (prompt "subject")
        html         (prompt-optional "html")
        text         (prompt-optional "text")
        bcc          (prompt-optional "bcc (vírgula para múltiplos)")
        cc           (prompt-optional "cc (vírgula para múltiplos)")
        reply-to     (prompt-optional "reply-to (vírgula para múltiplos)")
        scheduled-at (prompt-optional "scheduled-at (ISO-8601, ex: 2026-12-01T09:00:00Z)")
        tags-raw     (prompt-optional "tags (ex: category=welcome,source=api)")
        headers-raw  (prompt-optional "headers (ex: X-Custom=valor,X-Outro=abc)")
        attachments  (prompt-attachments)]
    (print-result
      (emails/send! client
        (cond-> {:from    from
                 :to      (split-csv to)
                 :subject subject}
          html         (assoc :html html)
          text         (assoc :text text)
          bcc          (assoc :bcc (split-csv bcc))
          cc           (assoc :cc (split-csv cc))
          reply-to     (assoc :reply-to (split-csv reply-to))
          scheduled-at (assoc :scheduled-at scheduled-at)
          tags-raw     (assoc :tags (parse-name-value-pairs tags-raw))
          headers-raw  (assoc :headers (parse-name-value-pairs headers-raw))
          attachments  (assoc :attachments attachments))))))

(defn action-send-batch! []
  (println "\n--- Enviar lote de e-mails ---")
  (println "Informe os e-mails um a um. Digite 'pronto' quando terminar.")
  (loop [emails-acc []]
    (println (str "\nE-mail #" (inc (count emails-acc))))
    (let [from    (prompt "  from")
          to      (prompt "  to")
          subject (prompt "  subject")
          text    (prompt-optional "  text")
          entry   (cond-> {:from from
                           :to   (split-csv to)
                           :subject subject}
                    text (assoc :text text))
          acc     (conj emails-acc entry)
          mais?   (prompt "  Adicionar mais? (s/n)")]
      (if (= "s" (clojure.string/lower-case (or mais? "n")))
        (recur acc)
        (print-result (emails/send-batch! client acc))))))

(defn action-get-email! []
  (println "\n--- Buscar e-mail por ID ---")
  (let [id (prompt "email-id")]
    (print-result (emails/get-email! client id))))

(defn action-update-email! []
  (println "\n--- Atualizar e-mail agendado ---")
  (let [id           (prompt "email-id")
        scheduled-at (prompt "novo scheduled-at (ISO-8601, ex: 2026-12-01T09:00:00Z)")]
    (print-result (emails/update-email! client id {:scheduled-at scheduled-at}))))

(defn action-cancel-email! []
  (println "\n--- Cancelar e-mail agendado ---")
  (let [id (prompt "email-id")]
    (print-result (emails/cancel-email! client id))))

(defn action-list-emails! []
  (println "\n--- Listar e-mails ---")
  (let [limit  (prompt-optional "limit (1-100)")
        after  (prompt-optional "after (cursor)")
        before (prompt-optional "before (cursor)")
        params (cond-> {}
                 limit  (assoc :limit (parse-long limit))
                 after  (assoc :after after)
                 before (assoc :before before))]
    (print-result (emails/list-emails! client (not-empty params)))))

(defn action-share-email! []
  (println "\n--- Compartilhar e-mail ---")
  (let [id         (prompt "email-id")
        expires-in (prompt-optional "expires-in (ex: 24h, 1 day)")]
    (print-result
      (emails/share-email! client id
        (when expires-in {:expires-in expires-in})))))

(defn action-get-attachment! []
  (println "\n--- Buscar anexo ---")
  (let [email-id      (prompt "email-id")
        attachment-id (prompt "attachment-id")]
    (print-result (emails/get-attachment! client email-id attachment-id))))

(defn action-list-attachments! []
  (println "\n--- Listar anexos ---")
  (let [email-id (prompt "email-id")
        limit    (prompt-optional "limit (1-100)")
        after    (prompt-optional "after (cursor)")
        before   (prompt-optional "before (cursor)")
        params   (cond-> {}
                   limit  (assoc :limit (parse-long limit))
                   after  (assoc :after after)
                   before (assoc :before before))]
    (print-result (emails/list-attachments! client email-id (not-empty params)))))

(defn action-get-metrics! []
  (println "\n--- Métricas de e-mails ---")
  (let [start-date   (prompt-optional "start-date (ISO-8601, ex: 2026-07-01)")
        end-date     (prompt-optional "end-date (ISO-8601)")
        timezone     (prompt-optional "timezone (ex: America/Sao_Paulo)")
        granularity  (prompt-optional "granularity (hourly|daily|weekly|monthly)")
        metrics      (prompt-optional "metrics (vírgula: sent,delivered,open_rate)")
        dimensions   (prompt-optional "dimensions (vírgula: period,domain)")
        params       (cond-> {}
                       start-date  (assoc :start-date start-date)
                       end-date    (assoc :end-date end-date)
                       timezone    (assoc :timezone timezone)
                       granularity (assoc :granularity granularity)
                       metrics     (assoc :metrics
                                     (mapv clojure.string/trim
                                       (clojure.string/split metrics #",")))
                       dimensions  (assoc :dimensions
                                     (mapv clojure.string/trim
                                       (clojure.string/split dimensions #","))))]
    (print-result (emails/get-metrics! client (not-empty params)))))

;; ---------------------------------------------------------------------------
;; Menu principal
;; ---------------------------------------------------------------------------

(def menu-items
  [[1  "Enviar e-mail              (emails/send!)"             action-send!]
   [2  "Enviar lote de e-mails     (emails/send-batch!)"       action-send-batch!]
   [3  "Buscar e-mail por ID       (emails/get-email!)"        action-get-email!]
   [4  "Atualizar e-mail agendado  (emails/update-email!)"     action-update-email!]
   [5  "Cancelar e-mail agendado   (emails/cancel-email!)"     action-cancel-email!]
   [6  "Listar e-mails             (emails/list-emails!)"      action-list-emails!]
   [7  "Compartilhar e-mail        (emails/share-email!)"      action-share-email!]
   [8  "Buscar anexo               (emails/get-attachment!)"   action-get-attachment!]
   [9  "Listar anexos              (emails/list-attachments!)" action-list-attachments!]
   [10 "Métricas de e-mails        (emails/get-metrics!)"      action-get-metrics!]
   [0  "Sair" nil]])

(defn print-menu []
  (println "\n╔══════════════════════════════════════════════════╗")
  (println   "║            resend-clojure  —  Emails API         ║")
  (println   "╚══════════════════════════════════════════════════╝")
  (doseq [[n label _] menu-items]
    (println (format "  [%2d] %s" n label)))
  (println))

(defn run-menu []
  (loop []
    (print-menu)
    (let [raw    (prompt "Opção")
          choice (parse-long (or raw ""))]
      (cond
        (nil? choice)
        (do (println "Opção inválida.") (recur))

        (= choice 0)
        (println "\nAté mais!\n")

        :else
        (let [found (first (filter #(= (first %) choice) menu-items))]
          (if found
            (do ((nth found 2)) (recur))
            (do (println "Opção inválida.") (recur))))))))

(run-menu)
