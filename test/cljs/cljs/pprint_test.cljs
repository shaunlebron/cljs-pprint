(ns cljs.pprint-test
  (:require
    [cemerick.cljs.test :as t]
    [cljs.pprint :refer [pprint cl-format]])
  (:require-macros
    [cemerick.cljs.test :refer [deftest is]]
    [cljs.pprint-test :refer [simple-tests]]))

(simple-tests print-length-tests
  (binding [*print-length* 1] (with-out-str (pprint '(a b c d e f))))
  "(a ...)\n"
  (binding [*print-length* 2] (with-out-str (pprint '(a b c d e f))))
  "(a b ...)\n"
  (binding [*print-length* 6] (with-out-str (pprint '(a b c d e f))))
  "(a b c d e f)\n"
  (binding [*print-length* 8] (with-out-str (pprint '(a b c d e f))))
  "(a b c d e f)\n"

  (binding [*print-length* 1] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 ...]\n"
  (binding [*print-length* 2] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 2 ...]\n"
  (binding [*print-length* 6] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 2 3 4 5 6]\n"
  (binding [*print-length* 8] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 2 3 4 5 6]\n"

  (binding [*print-length* 1] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 ...}\n"
  (binding [*print-length* 2] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 2 ...}\n"
  (binding [*print-length* 6] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 2 3 4 5 6}\n"
  (binding [*print-length* 8] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 2 3 4 5 6}\n"

  (binding [*print-length* 1] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, ...}\n"
  (binding [*print-length* 2] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, 3 4, ...}\n"
  (binding [*print-length* 6] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, 3 4, 5 6, 7 8, 9 10, 11 12}\n"
  (binding [*print-length* 8] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, 3 4, 5 6, 7 8, 9 10, 11 12}\n"

  #_(comment "Not implemented"
           (binding [*print-length* 1] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
           "[1, ...]\n"
           (binding [*print-length* 2] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
           "[1, 2, ...]\n"
           (binding [*print-length* 6] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
           "[1, 2, 3, 4, 5, 6]\n"
           (binding [*print-length* 8] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
           "[1, 2, 3, 4, 5, 6]\n"
           )
)

(simple-tests print-margin-tests
  (binding [cljs.pprint/*print-right-margin* 20]
    (with-out-str (pprint (sorted-map 1 (sorted-map 12345 123456), 3 (sorted-map 4 5, 6 7)))))
  "{1 {12345 123456},\n 3 {4 5, 6 7}}\n"

  (binding [cljs.pprint/*print-right-margin* 8]
    (with-out-str (pprint (sorted-set 123 456 789))))
  "#{123\n  456\n  789}\n"
)

;;----------------------------------------------------------------------------
;; clj-format tests
;;----------------------------------------------------------------------------

(simple-tests d-tests
  (cl-format nil "~D" 0) "0"
  (cl-format nil "~D" 2e6) "2000000"
  (cl-format nil "~D" 2000000) "2000000"
  (cl-format nil "~:D" 2000000) "2,000,000"
  ;(cl-format nil "~D" 1/2) "1/2" ;no ratio
  (cl-format nil "~D" 'fred) "fred"
)

(simple-tests cardinal-tests
  (cl-format nil "~R" 0) "zero"
  (cl-format nil "~R" 4) "four"
  (cl-format nil "~R" 15) "fifteen"
  (cl-format nil "~R" -15) "minus fifteen"
  (cl-format nil "~R" 25) "twenty-five"
  (cl-format nil "~R" 20) "twenty"
  (cl-format nil "~R" 200) "two hundred"
  (cl-format nil "~R" 203) "two hundred three"

  (cl-format nil "~R" 44879032)
  "forty-four million, eight hundred seventy-nine thousand, thirty-two"

  (cl-format nil "~R" -44879032)
  "minus forty-four million, eight hundred seventy-nine thousand, thirty-two"

  (cl-format nil "~R = ~:*~:D" 44000032)
  "forty-four million, thirty-two = 44,000,032"

  ;;js/Number.MAX_SAFE_INTEGER - js starts munging larger values
  (cl-format nil "~R = ~:*~:D" 9007199254740991)
  "nine quadrillion, seven trillion, one hundred ninety-nine billion, two hundred fifty-four million, seven hundred forty thousand, nine hundred ninety-one = 9,007,199,254,740,991"

  ;;js/Number.MAX_SAFE_INTEGER - js starts munging smaller values
  (cl-format nil "~R = ~:*~:D" -9007199254740991)
  "minus nine quadrillion, seven trillion, one hundred ninety-nine billion, two hundred fifty-four million, seven hundred forty thousand, nine hundred ninety-one = -9,007,199,254,740,991"

  ;;TODO Figure out what to do with larger numbers
  #_(comment "These fail due to javascript's large number handling"
  (cl-format nil "~R = ~:*~:D" 448790329480948209384389429384029384029842098420989842094)
  "four hundred forty-eight septendecillion, seven hundred ninety sexdecillion, three hundred twenty-nine quindecillion, four hundred eighty quattuordecillion, nine hundred forty-eight tredecillion, two hundred nine duodecillion, three hundred eighty-four undecillion, three hundred eighty-nine decillion, four hundred twenty-nine nonillion, three hundred eighty-four octillion, twenty-nine septillion, three hundred eighty-four sextillion, twenty-nine quintillion, eight hundred forty-two quadrillion, ninety-eight trillion, four hundred twenty billion, nine hundred eighty-nine million, eight hundred forty-two thousand, ninety-four = 448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094"

  (cl-format nil "~R = ~:*~:D" 448790329480948209384389429384029384029842098420989842094490320942058747587584758375847593475)
  "448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094,490,320,942,058,747,587,584,758,375,847,593,475 = 448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094,490,320,942,058,747,587,584,758,375,847,593,475"
  )

  (cl-format nil "~R = ~:*~:D" 2e6)
  "two million = 2,000,000"

  (cl-format nil "~R = ~:*~:D" 200000200000)
  "two hundred billion, two hundred thousand = 200,000,200,000"
)

(simple-tests ordinal-tests
  (cl-format nil "~:R" 0) "zeroth"
  (cl-format nil "~:R" 4) "fourth"
  (cl-format nil "~:R" 15) "fifteenth"
  (cl-format nil "~:R" -15) "minus fifteenth"
  (cl-format nil "~:R" 25) "twenty-fifth"
  (cl-format nil "~:R" 20) "twentieth"
  (cl-format nil "~:R" 200) "two hundredth"
  (cl-format nil "~:R" 203) "two hundred third"

  (cl-format nil "~:R" 44879032)
  "forty-four million, eight hundred seventy-nine thousand, thirty-second"

  (cl-format nil "~:R" -44879032)
  "minus forty-four million, eight hundred seventy-nine thousand, thirty-second"

  (cl-format nil "~:R = ~:*~:D" 44000032)
  "forty-four million, thirty-second = 44,000,032"

  ;;js/Number.MAX_SAFE_INTEGER - js starts munging larger values
  (cl-format nil "~:R = ~:*~:D" 9007199254740991)
  "nine quadrillion, seven trillion, one hundred ninety-nine billion, two hundred fifty-four million, seven hundred forty thousand, nine hundred ninety-first = 9,007,199,254,740,991"

  ;;js/Number.MAX_SAFE_INTEGER - js starts munging smaller values
  (cl-format nil "~:R = ~:*~:D" -9007199254740991)
  "minus nine quadrillion, seven trillion, one hundred ninety-nine billion, two hundred fifty-four million, seven hundred forty thousand, nine hundred ninety-first = -9,007,199,254,740,991"

  ;;TODO Figure out what to do with larger numbers
  #_(comment "These fail due to javascript's large number handling"
  (cl-format nil "~:R = ~:*~:D" 448790329480948209384389429384029384029842098420989842094)
  "four hundred forty-eight septendecillion, seven hundred ninety sexdecillion, three hundred twenty-nine quindecillion, four hundred eighty quattuordecillion, nine hundred forty-eight tredecillion, two hundred nine duodecillion, three hundred eighty-four undecillion, three hundred eighty-nine decillion, four hundred twenty-nine nonillion, three hundred eighty-four octillion, twenty-nine septillion, three hundred eighty-four sextillion, twenty-nine quintillion, eight hundred forty-two quadrillion, ninety-eight trillion, four hundred twenty billion, nine hundred eighty-nine million, eight hundred forty-two thousand, ninety-fourth = 448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094"
  (cl-format nil "~:R = ~:*~:D" 448790329480948209384389429384029384029842098420989842094490320942058747587584758375847593475)
  "448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094,490,320,942,058,747,587,584,758,375,847,593,475th = 448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094,490,320,942,058,747,587,584,758,375,847,593,475"
  (cl-format nil "~:R = ~:*~:D" 448790329480948209384389429384029384029842098420989842094490320942058747587584758375847593471)
  "448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094,490,320,942,058,747,587,584,758,375,847,593,471st = 448,790,329,480,948,209,384,389,429,384,029,384,029,842,098,420,989,842,094,490,320,942,058,747,587,584,758,375,847,593,471"
  )

  (cl-format nil "~:R = ~:*~:D" 2e6)
  "two millionth = 2,000,000"
)
