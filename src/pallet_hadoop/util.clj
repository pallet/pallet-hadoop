(ns pallet-hadoop.util
  [clojure.set :only (difference)])

;; ### Utilities

(defn merge-to-vec
  "Returns a vector representation of the union of all supplied
  items. Entries in xs can be collections or individual items. For
  example,

  (merge-to-vec [1 2] :help 2 1)
  => [1 2 :help]"
  [& xs]
  (->> xs
       (map #(if (coll? %) (set %) #{%}))
       (reduce #(concat % (difference %2 (set %))))
       (vec)))

(defn set-vals
  "Sets all entries of the supplied map equal to the supplied value."
  [map val]
  (zipmap (keys map)
          (repeat val)))

(defn getmerge [m coll]
  (->> (map m coll)
       (apply merge-to-vec)))
