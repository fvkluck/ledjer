(ns ledjer.core
  (:require [clojure.algo.generic.functor :refer [fmap]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [environ.core :refer [env]]
            [lentes.core :as l]
            [java-time :refer [local-date-time local-date as truncate-to]])
  (:gen-class))

(defn add-transaction [journal t]
  (update journal :transactions conj t))

(defn add-posting [journal p]
  "Add posting p to last transaction of journal. Throws exception if there is no last transaction."
  (let [transactions (:transactions journal)
        last-transaction (peek transactions)
        new-transaction (update last-transaction :postings conj p)]
        (if last-transaction (assoc journal :transactions (conj (pop transactions) new-transaction)))))


(defn parse-include-line [line]
  (if-let [[_ file-name] (re-matches #"include (\S+)" line)]
    {:include file-name}))

(defn parse-commodity-line [line]
  (if-let [[_ fmt] (re-matches #"commodity (.*)" line)]
    {:commodity fmt}))

(defn parse-budget-header [x]
  (if-let [[_ period] (re-matches #"~(.*)" x)]
    {:budget period}))

(defn parse-transaction-header [x]
  (if-let [[_ date description]
        (re-matches #"(\d\d\d\d/\d\d/\d\d) (.*)" x)]
    {:date (local-date "yyyy/MM/dd" date)
     :description description}))

(defn parse-posting [x]
  (if-let [[_ account amount]
        (re-matches #"\s*([\S:]+)\s+(.*) EUR" x)]
    {:account account :amount (bigdec (edn/read-string amount))}))

(defn parse-account-name [x]
  (if-let [matches (re-seq #"(\w+)" x)]
    (map #(nth % 1) matches)))

(defn parse-empty-line [x]
  (if (re-matches #"" x)
    {:empty-line true}))

(defn reduce-fn [acc line]
  (if-let [header ((some-fn parse-include-line parse-commodity-line parse-budget-header) line)]
    (update acc :headers conj header)
    (if-let [transaction-header (parse-transaction-header line)]
      (add-transaction acc transaction-header)
      (if-let [posting (parse-posting line)]
        (add-posting acc posting)
        acc))))

(defn read-ledger-file [contents]
  (->> contents
       (string/split-lines)
       (reduce reduce-fn {:headers [] :transactions []})))

(defn accounts [journal]
  (->> (:transactions journal)
      (mapcat :postings)
      (map :account)
      (distinct)
      (sort)))

(defn balancesheet [transactions]
  (->> transactions
       (fmap (partial map :amount))
       (fmap (partial apply +))))

(defn make-posting [account amount]
  {:account account :amount amount})

(defn make-transaction [date description postings]
  {:description description
   :date date
   :postings (map (partial apply make-posting) postings)})

(defn monthly [transactions]
  (->> transactions
       (group-by #(as (:date %) :year :month-of-year))))

(defn account-view [transactions]
  (->> transactions
       (mapcat (fn [{date :date
                     description :description
                     postings :postings}]
                 (for [p postings]
                   (let [{account :account} p]
                     {account [(assoc p :date date :description description)]}))))
       (apply merge-with into)))

(defn lpad [length s]
  (format (str "%" length "s") s))

(defn rpad [length s]
  (format (str "%-" length "s") s))

(defn make-report
  "Build a report of the transactions. Output format is a map with [account period] as key and the aggregated value as value."
  ([transactions] (make-report balancesheet transactions))
  ([make-report-fn transactions]
   (->> transactions
        (account-view)
        (fmap monthly)
        (fmap make-report-fn)
        (mapcat (fn [[account values]]
                  (for [[header data] values]
                    {[account header] data})))
        (apply merge-with into))))

(defn transpose [m]
  (apply mapv vector m))

(defn matrixmap [f m]
  (mapv (partial mapv f) m))

(defn table->string [rheaders cheaders data]
  (let [first-width (->> rheaders
                         (mapv count)
                         (apply max))
        padded-rheaders (->> rheaders
                             (mapv (partial rpad first-width)))
        str-data (matrixmap str data)
        widths (->> str-data
                    (transpose)
                    (matrixmap count)
                    (mapv (partial apply max)))
        padded-data (->> str-data
                         (mapv (fn [row]
                                 (mapv lpad widths row))))
        first-row (into [(lpad first-width "")] (mapv rpad widths cheaders))
        rows (mapv (fn [rh row]
                     (into [rh] row))
                   padded-rheaders padded-data)
        ]
    (string/join "\n" (mapv (partial string/join " | ") (into [first-row] rows)))))

(def cli-options
  [["-f" "--file NAME" "File name to use" ]
   ["-h" "--help"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing the arguments:"
       (string/join \newline errors)))

(defn usage [options-summary]
  (->> ["This is ledjer. Ledjer is a cli-bookkeeping program based on programs like ledger and hledger,"
        "implemented in Clojure."
        "It's more a learning project that meant for regular use."
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  balancesheet    Display a balancesheet"
        ""
        "Please refer to the source code for more information ;-)"]
       (string/join \newline)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) {:ok? true :exit-message "help!"}
      errors {:ok? false :exit-message "error"}
      (and (= 1 (count arguments))
           (#{"accounts" "balancesheet" "bs"} (first arguments)))
      {:action (first arguments) :options options}
      :else {:ok? false :exit-message (usage options)})))

(defn accounts! [journal options]
  (let [report (accounts journal)]
    (println (string/join "\n" report))))

(defn balancesheet! [journal options]
  (let [report (->> (:transactions journal)
                    (make-report balancesheet))
        rheaders (sort (distinct (map first (keys report))))
        cheaders (sort (distinct (map second (keys report))))
        data (for [rh rheaders]
               (for [ch cheaders]
                 (get report [rh ch])))]
    (println (table->string rheaders cheaders data))))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 1 0) exit-message)
      (let [file-name (or (:file options)
                          (env :ledjer-file))
            journal (-> file-name
                        (slurp)
                        (read-ledger-file))]
        (cond
          (#{"accounts"} action) (accounts! journal options)
          (#{"balancesheet" "bs"} action) (balancesheet! journal options))))))
