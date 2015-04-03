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

(simple-tests ordinal1-tests
  (cl-format nil "~:R" 1) "first"
  (cl-format nil "~:R" 11) "eleventh"
  (cl-format nil "~:R" 21) "twenty-first"
  (cl-format nil "~:R" 20) "twentieth"
  (cl-format nil "~:R" 220) "two hundred twentieth"
  (cl-format nil "~:R" 200) "two hundredth"
  (cl-format nil "~:R" 999) "nine hundred ninety-ninth"
)

(simple-tests roman-tests
  (cl-format nil "~@R" 3) "III"
  (cl-format nil "~@R" 4) "IV"
  (cl-format nil "~@R" 9) "IX"
  (cl-format nil "~@R" 29) "XXIX"
  (cl-format nil "~@R" 429) "CDXXIX"
  (cl-format nil "~@:R" 429) "CCCCXXVIIII"
  (cl-format nil "~@:R" 3429) "MMMCCCCXXVIIII"
  (cl-format nil "~@R" 3429) "MMMCDXXIX"
  (cl-format nil "~@R" 3479) "MMMCDLXXIX"
  (cl-format nil "~@R" 3409) "MMMCDIX"
  (cl-format nil "~@R" 300) "CCC"
  (cl-format nil "~@R ~D" 300 20) "CCC 20"
  (cl-format nil "~@R" 5000) "5,000"
  (cl-format nil "~@R ~D" 5000 20) "5,000 20"
  (cl-format nil "~@R" "the quick") "the quick"
)

(simple-tests c-tests
  (cl-format nil "~{~c~^, ~}~%" "hello") "h, e, l, l, o\n"
  (cl-format nil "~{~:c~^, ~}~%" "hello") "h, e, l, l, o\n"
  (cl-format nil "~@C~%" \m) "\\m\n"
  (cl-format nil "~@C~%" (char 222)) "\\Þ\n"

  ;;chars that are specially printed in cljs
  (cl-format nil "~@C~%" (char 8)) "\\backspace\n"
  (cl-format nil "~@C~%" (char 9)) "\\tab\n"
  (cl-format nil "~@C~%" (char 10)) "\\newline\n"
  (cl-format nil "~@C~%" (char 12)) "\\formfeed\n"
  (cl-format nil "~@C~%" (char 13)) "\\return\n"
  (cl-format nil "~@C~%" (char 34)) "\\\"\n"
  (cl-format nil "~@C~%" (char 92)) "\\\\\n"

  (cl-format nil "~@C~%" (char 3)) "\\\n"
)

(simple-tests e-tests
  (cl-format nil "*~E*" 0.0) "*0.0E+0*"
  (cl-format nil "*~6E*" 0.0) "*0.0E+0*"
  (cl-format nil "*~6,0E*" 0.0) "* 0.E+0*"
  (cl-format nil "*~7,2E*" 0.0) "*0.00E+0*"
  (cl-format nil "*~5E*" 0.0) "*0.E+0*"
  (cl-format nil "*~10,2,2,,'?E*" 2.8E120) "*??????????*"
  (cl-format nil "*~10,2E*" 9.99999) "*   1.00E+1*"
  (cl-format nil "*~10,2E*" 9.99999E99) "* 1.00E+100*"
  (cl-format nil "*~10,2,2E*" 9.99999E99) "* 1.00E+100*"
  (cl-format nil "*~10,2,2,,'?E*" 9.99999E99) "*??????????*"
)

(simple-tests $-tests
  (cl-format nil "~$" 22.3) "22.30"
  (cl-format nil "~$" 22.375) "22.38"
  (cl-format nil "~3,5$" 22.375) "00022.375"
  (cl-format nil "~3,5,8$" 22.375) "00022.375"
  (cl-format nil "~3,5,10$" 22.375) " 00022.375"
  (cl-format nil "~3,5,14@$" 22.375) "    +00022.375"
  (cl-format nil "~3,5,14@$" 22.375) "    +00022.375"
  (cl-format nil "~3,5,14@:$" 22.375) "+    00022.375"
  (cl-format nil "~3,,14@:$" 0.375) "+        0.375"
  (cl-format nil "~1,1$" -12.0) "-12.0"
  (cl-format nil "~1,1$" 12.0) "12.0"
  (cl-format nil "~1,1$" 12.0) "12.0"
  (cl-format nil "~1,1@$" 12.0) "+12.0"
  (cl-format nil "~1,1,8,' @:$" 12.0) "+   12.0"
  (cl-format nil "~1,1,8,' @$" 12.0) "   +12.0"
  (cl-format nil "~1,1,8,' :$" 12.0) "    12.0"
  (cl-format nil "~1,1,8,' $" 12.0) "    12.0"
  (cl-format nil "~1,1,8,' @:$" -12.0) "-   12.0"
  (cl-format nil "~1,1,8,' @$" -12.0) "   -12.0"
  (cl-format nil "~1,1,8,' :$" -12.0) "-   12.0"
  (cl-format nil "~1,1,8,' $" -12.0) "   -12.0"
  (cl-format nil "~1,1$" 0.001) "0.0"
  (cl-format nil "~2,1$" 0.001) "0.00"
  (cl-format nil "~1,1,6$" 0.001) "   0.0"
  (cl-format nil "~1,1,6$" 0.0015) "   0.0"
  (cl-format nil "~2,1,6$" 0.005) "  0.01"
  (cl-format nil "~2,1,6$" 0.01) "  0.01"
  (cl-format nil "~$" 0.099) "0.10"
  (cl-format nil "~1$" 0.099) "0.1"
  (cl-format nil "~1$" 0.1) "0.1"
  (cl-format nil "~1$" 0.99) "1.0"
  (cl-format nil "~1$" -0.99) "-1.0"
)

(simple-tests f-tests
  (cl-format nil "~,1f" -12.0) "-12.0"
  (cl-format nil "~,0f" 9.4) "9."
  (cl-format nil "~,0f" 9.5) "10."
  (cl-format nil "~,0f" -0.99) "-1."
  (cl-format nil "~,1f" -0.99) "-1.0"
  (cl-format nil "~,2f" -0.99) "-0.99"
  (cl-format nil "~,3f" -0.99) "-0.990"
  (cl-format nil "~,0f" 0.99) "1."
  (cl-format nil "~,1f" 0.99) "1.0"
  (cl-format nil "~,2f" 0.99) "0.99"
  (cl-format nil "~,3f" 0.99) "0.990"
  (cl-format nil "~,3f" -0.099) "-0.099"
  (cl-format nil "~,4f" -0.099) "-0.0990"
  (cl-format nil "~,5f" -0.099) "-0.09900"
  (cl-format nil "~,3f" 0.099) "0.099"
  (cl-format nil "~,4f" 0.099) "0.0990"
  (cl-format nil "~,5f" 0.099) "0.09900"
  (cl-format nil "~f" -1) "-1.0"
  (cl-format nil "~2f" -1) "-1."
  (cl-format nil "~3f" -1) "-1."
  (cl-format nil "~4f" -1) "-1.0"
  (cl-format nil "~8f" -1) "    -1.0"
  (cl-format nil "~2f" -0.0099) "-0."
  (cl-format nil "~3f" -0.0099) "-0."
  (cl-format nil "~4f" -0.0099) "-.01"
  (cl-format nil "~5f" -0.0099) "-0.01"
  (cl-format nil "~6f" -0.0099) "-.0099"
  (cl-format nil "~1f" 0.0099) "0."
  (cl-format nil "~2f" 0.0099) "0."
  (cl-format nil "~3f" 0.0099) ".01"
  (cl-format nil "~4f" 0.0099) "0.01"
  (cl-format nil "~5f" 0.0099) ".0099"
  (cl-format nil "~6f" 0.0099) "0.0099"
  (cl-format nil "~1f" -0.099) "-.1"
  (cl-format nil "~2f" -0.099) "-.1"
  (cl-format nil "~3f" -0.099) "-.1"
  (cl-format nil "~4f" -0.099) "-0.1"
  (cl-format nil "~5f" -0.099) "-.099"
  (cl-format nil "~6f" -0.099) "-0.099"
  (cl-format nil "~1f" 0.099) ".1"
  (cl-format nil "~2f" 0.099) ".1"
  (cl-format nil "~3f" 0.099) "0.1"
  (cl-format nil "~4f" 0.099) ".099"
  (cl-format nil "~5f" 0.099) "0.099"
  (cl-format nil "~1f" -0.99) "-1."
  (cl-format nil "~2f" -0.99) "-1."
  (cl-format nil "~3f" -0.99) "-1."
  (cl-format nil "~4f" -0.99) "-.99"
  (cl-format nil "~5f" -0.99) "-0.99"
  (cl-format nil "~1f" 0.99) "1."
  (cl-format nil "~2f" 0.99) "1."
  (cl-format nil "~3f" 0.99) ".99"
  (cl-format nil "~4f" 0.99) "0.99"
  (cl-format nil "~1f" 111.11111) "111."
  (cl-format nil "~4f" 111.11111) "111."
  (cl-format nil "~5f" 111.11111) "111.1"
  (cl-format nil "~1f" -111.11111) "-111."
  (cl-format nil "~5f" -111.11111) "-111."
  (cl-format nil "~6f" -111.11111) "-111.1"
  (cl-format nil "~1f" 555.55555) "556."
  (cl-format nil "~4f" 555.55555) "556."
  (cl-format nil "~5f" 555.55555) "555.6"
  (cl-format nil "~8f" 555.55555) "555.5556"
  (cl-format nil "~1f" -555.55555) "-556."
  (cl-format nil "~5f" -555.55555) "-556."
  (cl-format nil "~6f" -555.55555) "-555.6"
  (cl-format nil "~8f" -555.55555) "-555.556"
  (cl-format nil "~1f" 999.999) "1000."
  (cl-format nil "~5f" 999.999) "1000."
  (cl-format nil "~6f" 999.999) "1000.0"
  (cl-format nil "~7f" 999.999) "999.999"
  (cl-format nil "~8f" 999.999) " 999.999"
  (cl-format nil "~1f" -999.999) "-1000."
  (cl-format nil "~6f" -999.999) "-1000."
  (cl-format nil "~7f" -999.999) "-1000.0"
  (cl-format nil "~8f" -999.999) "-999.999"
  (cl-format nil "~5,2f" 111.11111) "111.11"
  (cl-format nil "~3,1f" -0.0099) "-.0"
  (cl-format nil "~6,4f" -0.0099) "-.0099"
  (cl-format nil "~6,5f" -0.0099) "-.00990"
  (cl-format nil "~6,6f" -0.0099) "-.009900"
  (cl-format nil "~6,4f" 0.0099) "0.0099"
  (cl-format nil "~6,5f" 0.0099) ".00990"
  (cl-format nil "~6,6f" 0.0099) ".009900"
  (cl-format nil "~2,1f" 0.0099) ".0"
  (cl-format nil "~6,2f" -111.11111) "-111.11"
  (cl-format nil "~6,3f" -111.11111) "-111.111"
  (cl-format nil "~8,5f" -111.11111) "-111.11111"
  (cl-format nil "~12,10f" 1.23456789014) "1.2345678901"
  (cl-format nil "~12,10f" 1.23456789016) "1.2345678902"
  (cl-format nil "~13,10f" -1.23456789014) "-1.2345678901"
  (cl-format nil "~13,10f" -1.23456789016) "-1.2345678902"
  (cl-format nil "~1,1f" 0.1) ".1"
)

(simple-tests ampersand-tests
  (cl-format nil "The quick brown ~a jumped over ~d lazy dogs" 'elephant 5)
  "The quick brown elephant jumped over 5 lazy dogs"
  (cl-format nil "The quick brown ~&~a jumped over ~d lazy dogs" 'elephant 5)
  "The quick brown \nelephant jumped over 5 lazy dogs"
  (cl-format nil "The quick brown ~&~a jumped\n~& over ~d lazy dogs" 'elephant 5)
  "The quick brown \nelephant jumped\n over 5 lazy dogs"
  (cl-format nil "~&The quick brown ~&~a jumped\n~& over ~d lazy dogs" 'elephant 5)
  "The quick brown \nelephant jumped\n over 5 lazy dogs"
  (cl-format nil "~3&The quick brown ~&~a jumped\n~& over ~d lazy dogs" 'elephant 5)
  "\n\nThe quick brown \nelephant jumped\n over 5 lazy dogs"
  (cl-format nil "~@{~&The quick brown ~a jumped over ~d lazy dogs~}" 'elephant 5 'fox 10)
  "The quick brown elephant jumped over 5 lazy dogs\nThe quick brown fox jumped over 10 lazy dogs"
  (cl-format nil "I ~[don't ~:;d~&o ~]have one~%" 0) "I don't have one\n"
  (cl-format nil "I ~[don't ~:;d~&o ~]have one~%" 1) "I d\no have one\n"
)

(simple-tests t-tests
  (cl-format nil "~@{~&~A~8,4T~:*~A~}"
             'a 'aa 'aaa 'aaaa 'aaaaa 'aaaaaa 'aaaaaaa 'aaaaaaaa 'aaaaaaaaa 'aaaaaaaaaa)
  "a       a\naa      aa\naaa     aaa\naaaa    aaaa\naaaaa   aaaaa\naaaaaa  aaaaaa\naaaaaaa aaaaaaa\naaaaaaaa    aaaaaaaa\naaaaaaaaa   aaaaaaaaa\naaaaaaaaaa  aaaaaaaaaa"
  (cl-format nil "~@{~&~A~,4T~:*~A~}"
             'a 'aa 'aaa 'aaaa 'aaaaa 'aaaaaa 'aaaaaaa 'aaaaaaaa 'aaaaaaaaa 'aaaaaaaaaa)
  "a    a\naa   aa\naaa  aaa\naaaa aaaa\naaaaa    aaaaa\naaaaaa   aaaaaa\naaaaaaa  aaaaaaa\naaaaaaaa aaaaaaaa\naaaaaaaaa    aaaaaaaaa\naaaaaaaaaa   aaaaaaaaaa"
  (cl-format nil "~@{~&~A~2,6@T~:*~A~}" 'a 'aa 'aaa 'aaaa 'aaaaa 'aaaaaa 'aaaaaaa 'aaaaaaaa 'aaaaaaaaa 'aaaaaaaaaa)
  "a     a\naa    aa\naaa   aaa\naaaa  aaaa\naaaaa       aaaaa\naaaaaa      aaaaaa\naaaaaaa     aaaaaaa\naaaaaaaa    aaaaaaaa\naaaaaaaaa   aaaaaaaaa\naaaaaaaaaa  aaaaaaaaaa"
)
