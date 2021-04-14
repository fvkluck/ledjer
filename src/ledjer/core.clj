(ns ledjer.core
  (:require [clojure.algo.generic.functor :refer [fmap]]
            [clojure.string :as string]
            [ledjer.parser :as parser]
            [lentes.core :as l]
            [java-time :refer [local-date as]]))

(defn accounts [journal]
  (->> (:transactions journal)
      (mapcat :postings)
      (map :account)
      (distinct)
      (sort)))

(defn balancesheet [transactions]
  (->> transactions
       (mapcat :postings)
       (group-by :account)
       (fmap (partial map :amount))
       (fmap (partial apply +))
       (sort)))

(defn make-posting [account amount]
  {:account account :amount amount})

(defn make-transaction [date description postings]
  {:description description
   :date date
   :postings (map (partial apply make-posting) postings)})

(defn c* [fns]
  "Cartesian product of functions"
  (fn [values]
    (mapv (fn [f x] (f x)) fns values)))

(defn cmap [fns coll]
  "Map the cartesian product of fns over coll"
  (mapv (c* fns) coll))

(def data
  [["2021/01/01" "Buy an apple"
    [["expenses:groceries" 0.45]
     ["assets:checking" -0.45]]]
   ["2021/01/15" "Buy a lemon"
    [["expenses:lemons" 0.30]
     ["assets:checking" -0.30]]]
   ["2021/02/01" "Buy another apple"
    [["expenses:groceries" 0.45]
     ["assets:checking" -0.45]]]
   ["2021/02/03" "Buy a red grapefruit"
    [["expenses:grapegruits" 0.80]
     ["assets:checking" -0.80]]]])


(defn monthly [transactions]
  (->> transactions
       (group-by #(as (:date %) :year :month-of-year))))

(defn display-report [data]
  (print (string/join "\n" data)))

(defn report-column->string [data]
  (->> data
       (mapv #(string/join " " %))
       (string/join "\n")))

(report-column->string [["expenses:grapefruits" 200] ["expenses:lemons" 100] ["assets:checking" -300]])

(def transactions
  (-> "2021.journal"
      (slurp)
      (parser/read-ledger-file)
      (:transactions)))

(defn by-account [transactions]
  (->> transactions
       (mapcat (fn [{date :date
                     description :description
                     postings :postings}]
                 (for [p postings]
                   (let [{account :account amount :amount} p]
                     {account [{:date date :description description :amount amount}]}))))
       (apply merge-with into)))
