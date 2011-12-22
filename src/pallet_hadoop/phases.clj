(ns pallet-hadoop.phases
  (:use [pallet.resource.remote-file :only (remote-file)]))

(defn file-limits
  [session fd-limit user-seq]
  (-> session
      (remote-file
       "/etc/security/limits.conf"
       :content
       (->> user-seq
            (mapcat (fn [user]
                      [(format "%s\tsoft\tnofile\t%s" user fd-limit)
                       (format "%s\thard\tnofile\t%s" user fd-limit)]))
            (join "\n"))
       :no-versioning true
       :overwrite-changes true)))
