package pk.parhlou.student.data

/**
 * DEMO CONTENT written by Parhlou for the prototype: tutor notes, a chapter test and marking rubrics for
 * Mathematics 9, Unit 1 (Real Numbers). Nothing here is copied from a textbook and nothing is academically
 * approved. Unit tests check that every model answer earns full marks from its own rubric.
 */
object SampleContent {
    const val MATHS_9 = "pb-9-math"
    const val M1 = "m-1-1"
    const val M2 = "m-1-2"
    const val CHAPTER_TEST = "sample-chapter-test"

    private fun note(
        id: String, module: String, title: String, keywords: List<String>, answer: String, explanation: String,
        simple: String, examples: List<String>, steps: List<String>, check: String, checkAnswer: String,
        vararg accept: List<String>,
    ) = ConceptNote(id, MATHS_9, module, title, keywords, answer, explanation, simple, examples, steps, check, checkAnswer, accept.toList())

    val notes: List<ConceptNote> = listOf(
        note(
            "n-rational", M1, "Rational numbers",
            listOf("rational number", "rational numbers", "rational", "p/q", "fraction"),
            "A rational number is any number that can be written as p/q, where p and q are integers and q ≠ 0.",
            "Whole numbers, fractions, terminating decimals and recurring decimals are all rational, because each can be written as a fraction of two integers.",
            "If you can write it as a fraction of two whole numbers, it is rational.",
            listOf(
                "3/4, −5 (= −5/1), 0.25 (= 1/4) and 0.333… (= 1/3) are all rational.",
                "7 is rational because 7 = 7/1. So is 1.2, because 1.2 = 12/10 = 6/5.",
            ),
            listOf(
                "To show 0.75 is rational, write it as a fraction: 0.75 = 75/100.",
                "Simplify: divide top and bottom by 25 to get 3/4.",
                "3 and 4 are integers and 4 ≠ 0, so 0.75 is rational.",
            ),
            "Is −2.5 a rational number? Why?", "Yes: −2.5 = −5/2, a fraction of two integers.",
            listOf("yes"), listOf("-5/2"), listOf("rational", "!irrational", "!not rational"),
        ),
        note(
            "n-irrational", M1, "Irrational numbers",
            listOf("irrational number", "irrational numbers", "irrational", "√2", "√3", "π", "pi", "non terminating", "non recurring"),
            "An irrational number cannot be written as p/q with integers p and q (q ≠ 0). Its decimal goes on forever without repeating.",
            "The square root of any whole number that is not a perfect square is irrational. π is irrational too.",
            "If no fraction of whole numbers equals it exactly, it is irrational. Its decimal never ends and never repeats.",
            listOf(
                "√2 = 1.41421356…, √3, √5 and π are irrational.",
                "√9 is NOT irrational, because √9 = 3, which is rational.",
            ),
            listOf(
                "To decide whether √n is irrational, check whether n is a perfect square (1, 4, 9, 16, 25, …).",
                "If it is, √n is a whole number, so it is rational. Example: √16 = 4.",
                "If it is not, √n is irrational. Example: 7 is not a perfect square, so √7 is irrational.",
            ),
            "Is √25 rational or irrational?", "Rational, because √25 = 5.",
            listOf("rational", "!irrational", "!not rational"), listOf("=5"),
        ),
        note(
            "n-decimals", M1, "Terminating and recurring decimals",
            listOf("terminating", "recurring", "repeating", "decimal", "decimals", "p:terminat", "p:recur"),
            "A terminating decimal ends (0.375). A recurring decimal repeats a block of digits forever (0.333…). Both are rational.",
            "A fraction in lowest terms has a terminating decimal only when its denominator has no prime factors other than 2 and 5. A decimal that never ends and never repeats is irrational.",
            "Ends → terminating. Repeats forever → recurring. Both can be written as fractions.",
            listOf(
                "3/8 = 0.375 terminates. 2/3 = 0.666… recurs.",
                "1/7 = 0.142857142857… recurs with a block of 6 digits.",
            ),
            listOf(
                "Write the fraction in lowest terms.",
                "Find the prime factors of the denominator.",
                "If the only prime factors are 2 and 5, the decimal terminates. Otherwise it recurs.",
                "Example: 7/20 → 20 = 2 × 2 × 5, so it terminates (0.35). 5/6 → 6 = 2 × 3, so it recurs (0.8333…).",
            ),
            "Does 9/40 terminate or recur?", "It terminates: 40 = 2 × 2 × 2 × 5, and 9/40 = 0.225.",
            listOf("p:terminat"), listOf("0.225"),
        ),
        note(
            "n-recurring-fraction", M1, "Recurring decimal to fraction",
            listOf("convert", "as a fraction", "to a fraction", "into a fraction", "0.333", "0.777", "recurring to fraction", "change"),
            "Call the decimal x, multiply by 10 (or 100) to move one repeating block past the point, subtract, and solve for x.",
            "Subtracting makes the endless repeating part cancel, leaving a simple equation.",
            "Name it x, multiply so the repeat lines up, subtract, then divide.",
            listOf(
                "0.444… = 4/9.",
                "0.151515…: let x = 0.1515…, then 100x = 15.1515…, so 99x = 15 and x = 15/99 = 5/33.",
            ),
            listOf(
                "Let x = 0.777…",
                "Multiply by 10: 10x = 7.777…",
                "Subtract: 10x − x = 7.777… − 0.777…, so 9x = 7.",
                "x = 7/9.",
            ),
            "Write 0.222… as a fraction.", "0.222… = 2/9.",
            listOf("2/9"),
        ),
        note(
            "n-real", M1, "Real numbers",
            listOf("real number", "real numbers", "number line", "set of real"),
            "Real numbers are all the rational and irrational numbers together. Every real number is a point on the number line.",
            "Natural numbers, whole numbers, integers, fractions and surds such as √2 are all real numbers.",
            "Every number you can mark on the number line is a real number.",
            listOf(
                "−3, 0, 2/5, √2 and π are all real numbers.",
                "√(−4) is not a real number, because no real number squared gives −4.",
            ),
            listOf(
                "Classify −8: it equals −8/1, so it is rational (and an integer).",
                "Classify 0.6: it equals 6/10 = 3/5, so it is rational.",
                "Classify √11: 11 is not a perfect square, so √11 is irrational.",
                "All three are real numbers.",
            ),
            "Is every integer a real number?", "Yes. Every integer n equals n/1, so it is rational, and every rational number is real.",
            listOf("yes"),
        ),
        note(
            "n-commutative", M2, "Commutative property",
            listOf("commutative", "commutative property", "order"),
            "Commutative property: changing the order does not change the result. a + b = b + a and a × b = b × a.",
            "It holds for addition and multiplication of real numbers, but not for subtraction or division.",
            "You can swap the two numbers when adding or multiplying.",
            listOf(
                "4 + 9 = 9 + 4 = 13.",
                "3 × 7 = 7 × 3 = 21. But 5 − 2 ≠ 2 − 5, so subtraction is not commutative.",
            ),
            listOf(
                "Look at what changed between the two sides.",
                "If only the order of the numbers changed, it is the commutative property.",
                "Example: 2 + √3 = √3 + 2 → the order changed → commutative property of addition.",
            ),
            "Which property is this: 6 × 2 = 2 × 6?", "The commutative property of multiplication.",
            listOf("commutative"),
        ),
        note(
            "n-associative", M2, "Associative property",
            listOf("associative", "associative property", "grouping", "brackets"),
            "Associative property: changing the grouping does not change the result. (a + b) + c = a + (b + c) and (ab)c = a(bc).",
            "The numbers stay in the same order; only the brackets move.",
            "When adding or multiplying three numbers, you can move the brackets.",
            listOf(
                "(2 + 3) + 4 = 2 + (3 + 4) = 9.",
                "(2 × 5) × 3 = 2 × (5 × 3) = 30.",
            ),
            listOf(
                "Check that the numbers are in the same order on both sides.",
                "If only the brackets moved, it is the associative property.",
                "Example: (1 + 2) + 5 = 3 + 5 = 8 and 1 + (2 + 5) = 1 + 7 = 8.",
            ),
            "Which property is this: (1 + 2) + 5 = 1 + (2 + 5)?", "The associative property of addition.",
            listOf("associative"),
        ),
        note(
            "n-distributive", M2, "Distributive property",
            listOf("distributive", "distributive property", "expand", "multiply out"),
            "Distributive property: multiplication spreads over addition. a(b + c) = ab + ac.",
            "Multiply the number outside the bracket by each term inside, then add.",
            "Multiply the outside number by each number inside the bracket.",
            listOf(
                "3(4 + 5) = 3·4 + 3·5 = 12 + 15 = 27.",
                "2(x + 7) = 2x + 14.",
            ),
            listOf(
                "To verify 5(2 + 6) = 5·2 + 5·6, work out the left side: 5 × 8 = 40.",
                "Work out the right side: 10 + 30 = 40.",
                "Both sides are 40, so the property holds.",
            ),
            "Use the distributive property: 4(3 + 2) = ?", "4·3 + 4·2 = 12 + 8 = 20.",
            listOf("20"),
        ),
        note(
            "n-identity-inverse", M2, "Identity and inverse",
            listOf("identity", "inverse", "additive inverse", "multiplicative inverse", "additive identity", "multiplicative identity", "reciprocal"),
            "The additive identity is 0 and the multiplicative identity is 1. The additive inverse of a is −a. The multiplicative inverse of a (a ≠ 0) is 1/a.",
            "An identity leaves a number unchanged: a + 0 = a and a × 1 = a. An inverse brings you back to the identity: a + (−a) = 0 and a × 1/a = 1.",
            "Adding 0 or multiplying by 1 changes nothing. The inverse undoes the number.",
            listOf(
                "The additive inverse of −7 is 7, because −7 + 7 = 0.",
                "The multiplicative inverse of 2/3 is 3/2, because 2/3 × 3/2 = 1. Zero has no multiplicative inverse.",
            ),
            listOf(
                "For 5, the additive inverse is the number that adds to give 0: −5.",
                "The multiplicative inverse is the number that multiplies to give 1: 1/5.",
                "Check: 5 + (−5) = 0 and 5 × 1/5 = 1.",
            ),
            "What is the multiplicative inverse of 4?", "1/4, because 4 × 1/4 = 1.",
            listOf("1/4"), listOf("0.25"),
        ),
        note(
            "n-closure", M2, "Closure property",
            listOf("closure", "closure property", "closed"),
            "Closure property: the sum and the product of two real numbers are always real numbers.",
            "A set is closed under an operation when the result always stays inside the set.",
            "Add or multiply two real numbers and you always get a real number.",
            listOf(
                "2 + √3 is real, and 5 × (−1/2) = −5/2 is real.",
                "Natural numbers are not closed under subtraction: 3 − 5 = −2 is not a natural number.",
            ),
            listOf(
                "Pick any two numbers from the set.",
                "Do the operation.",
                "If the answer is always in the same set, the set is closed under that operation.",
            ),
            "Is the sum of two real numbers always a real number?", "Yes. That is the closure property of addition.",
            listOf("yes"),
        ),
        note(
            "n-surds", M1, "Simplifying square roots",
            listOf("surd", "surds", "simplify", "simplifying", "radical", "√50", "√12", "√18", "√72"),
            "To simplify √n, take out the largest perfect-square factor: √(a² × b) = a√b.",
            "Split the number into a perfect square times what is left, then take the square root of the perfect square.",
            "Find a square number inside, and bring its root outside.",
            listOf(
                "√12 = √(4 × 3) = 2√3.",
                "√72 = √(36 × 2) = 6√2.",
            ),
            listOf(
                "To simplify √50, find the largest perfect square that divides 50: it is 25.",
                "Write √50 = √(25 × 2).",
                "Split it: √25 × √2 = 5√2.",
            ),
            "Simplify √18.", "√18 = √(9 × 2) = 3√2.",
            listOf("3√2"),
        ),
        note(
            "n-proof-root2", M1, "Proof that √2 is irrational",
            listOf("prove", "proof", "contradiction", "√2 is irrational", "why is √2 irrational", "show that √2"),
            "We prove it by contradiction: assume √2 is rational and show this leads to something impossible.",
            "If √2 were a fraction in lowest terms, both the top and the bottom would have to be even, which a fraction in lowest terms cannot be.",
            "Pretend √2 is a fraction. The maths then says top and bottom are both even, so the fraction was not in lowest terms. That is impossible.",
            listOf(
                "The same method proves √3 is irrational, using divisibility by 3.",
                "It does not work for √4, because √4 = 2 = 2/1 really is rational.",
            ),
            listOf(
                "Assume √2 = p/q, where p and q are integers with no common factor and q ≠ 0.",
                "Square both sides: 2 = p²/q², so p² = 2q².",
                "So p² is even, which means p is even. Write p = 2k.",
                "Substitute: (2k)² = 2q², so 4k² = 2q² and q² = 2k². So q is even too.",
                "Then p and q are both even, so they share the factor 2. This contradicts step 1.",
                "So the assumption is false: √2 is irrational.",
            ),
            "In the proof, why must p be even?", "Because p² = 2q² is even, and only an even number has an even square.",
            listOf("even"), listOf("2q²"),
        ),
    )

    private fun crit(id: String, description: String, marks: Double, why: String, how: String, vararg accept: List<String>) =
        RubricCriterion(id, description, marks, accept.toList(), why, how)

    private fun mcq(id: String, stem: String, options: List<String>, correct: Int, rationales: List<String>, noteId: String) =
        McqQuestion(id, stem, options, correct, rationales, ReviewStatus.SAMPLE_UNREVIEWED, noteId)

    val moduleWritten: Map<String, List<WrittenQuestion>> = mapOf(
        M1 to listOf(
            WrittenQuestion(
                id = "w-1-1", text = "Explain why 0.75 is rational but √3 is irrational.", marks = 3.0,
                modelAnswer = listOf(
                    "0.75 = 75/100 = 3/4, a ratio of two integers, so it is rational.",
                    "3 is not a perfect square, so √3 cannot be written as p/q.",
                    "Its decimal 1.7320508… never ends or repeats, so √3 is irrational.",
                ),
                rubric = listOf(
                    crit("a", "Writes 0.75 as a fraction of two integers", 1.0,
                        "A number is rational only if it can be written as p/q.", "Write 0.75 = 75/100 = 3/4.",
                        listOf("3/4"), listOf("75/100")),
                    crit("b", "Gives a reason for √3", 1.0,
                        "A claim needs a reason to earn the mark.",
                        "Say that 3 is not a perfect square, or that the decimal of √3 never ends or repeats.",
                        listOf("not a perfect square"), listOf("not perfect square"), listOf("never ends"),
                        listOf("never repeats"), listOf("does not repeat"), listOf("non-terminating")),
                    crit("c", "Concludes that √3 is irrational", 1.0,
                        "The answer must end with a clear conclusion.", "Finish with: so √3 is irrational.",
                        listOf("irrational")),
                ),
                followUp = "Is 0.125 rational? Write it as p/q in lowest terms.", noteId = "n-irrational",
            ),
        ),
        M2 to listOf(
            WrittenQuestion(
                id = "w-1-2", text = "Show that 3(4 + 5) = 3·4 + 3·5 by working out both sides, and name the property.", marks = 3.0,
                modelAnswer = listOf(
                    "Left side: 3(4 + 5) = 3 × 9 = 27",
                    "Right side: 3·4 + 3·5 = 12 + 15 = 27",
                    "Both sides are 27. This is the distributive property.",
                ),
                rubric = listOf(
                    crit("a", "Works out the left side", 1.0,
                        "Both sides must be evaluated separately to show they are equal.", "Add inside the bracket first: 3 × 9 = 27.",
                        listOf("3*9"), listOf("3(9)"), listOf("3x9")),
                    crit("b", "Works out the right side", 1.0,
                        "Both sides must be evaluated separately to show they are equal.", "Multiply first, then add: 12 + 15 = 27.",
                        listOf("12+15")),
                    crit("c", "Names the distributive property", 1.0,
                        "The question asks you to name the property.", "Write: this is the distributive property.",
                        listOf("distributive")),
                ),
                followUp = "Verify 2(6 + 1) = 2·6 + 2·1 the same way.", noteId = "n-distributive",
            ),
        ),
    )

    private val paperMcqs = listOf(
        mcq("p1", "Which of these is a rational number?", listOf("√7", "π", "0.1010010001…", "√49"), 3,
            listOf("7 is not a perfect square, so √7 is irrational.", "π is irrational.",
                "This decimal never ends and never repeats, so it is irrational.", "Correct. √49 = 7, which is rational."), "n-irrational"),
        mcq("p2", "The decimal form of 5/8 is…", listOf("0.58", "0.625", "0.8", "1.6"), 1,
            listOf("0.58 = 58/100, not 5/8.", "Correct. 5 ÷ 8 = 0.625.", "0.8 = 4/5.", "1.6 = 8/5, the reciprocal."), "n-decimals"),
        mcq("p3", "The additive identity of the real numbers is…", listOf("1", "0", "−1", "a"), 1,
            listOf("1 is the multiplicative identity.", "Correct. a + 0 = a for every real number a.",
                "a + (−1) changes a.", "a + a = 2a, which changes a."), "n-identity-inverse"),
        mcq("p4", "The multiplicative inverse of −3/5 is…", listOf("3/5", "5/3", "−5/3", "−3/5"), 2,
            listOf("3/5 is the additive inverse.", "−3/5 × 5/3 = −1, not 1.",
                "Correct. −3/5 × −5/3 = 1.", "−3/5 × −3/5 = 9/25, not 1."), "n-identity-inverse"),
        mcq("p5", "0.444… written as a fraction is…", listOf("4/10", "4/9", "44/100", "2/5"), 1,
            listOf("4/10 = 0.4 exactly.", "Correct. If x = 0.444…, then 10x − x = 4, so x = 4/9.",
                "44/100 = 0.44 exactly.", "2/5 = 0.4 exactly."), "n-recurring-fraction"),
        mcq("p6", "√75 in simplest form is…", listOf("3√5", "25√3", "5√3", "15"), 2,
            listOf("(3√5)² = 45, not 75.", "(25√3)² = 1875, not 75.",
                "Correct. √75 = √(25 × 3) = 5√3.", "15² = 225, not 75."), "n-surds"),
        mcq("p7", "(a × b) × c = a × (b × c) is the…", listOf("commutative property", "associative property", "distributive property", "closure property"), 1,
            listOf("Commutative is about order, and the order here is unchanged.", "Correct. Only the grouping changed.",
                "Distributive links multiplication with addition.", "Closure says the result is still a real number."), "n-associative"),
        mcq("p8", "Which fraction has a recurring decimal?", listOf("1/4", "3/20", "7/12", "9/25"), 2,
            listOf("4 = 2 × 2, so 1/4 terminates (0.25).", "20 = 2 × 2 × 5, so 3/20 terminates (0.15).",
                "Correct. 12 = 2 × 2 × 3 has the factor 3, so 7/12 recurs (0.58333…).", "25 = 5 × 5, so 9/25 terminates (0.36)."), "n-decimals"),
        mcq("p9", "Between which two whole numbers does √20 lie?", listOf("3 and 4", "4 and 5", "5 and 6", "9 and 10"), 1,
            listOf("4² = 16 is already less than 20.", "Correct. 16 < 20 < 25, so 4 < √20 < 5.",
                "5² = 25 is more than 20.", "That would be near half of 20, not its square root."), "n-irrational"),
        mcq("p10", "The product of a non-zero rational number and an irrational number is always…", listOf("rational", "irrational", "an integer", "zero"), 1,
            listOf("If it were rational, dividing by the rational number would make the irrational number rational.",
                "Correct. For example 2 × √3 = 2√3, which is irrational.",
                "For example 2 × √3 is not an integer.", "It is zero only when the rational number is zero."), "n-irrational"),
    )

    private val shortQuestions = listOf(
        WrittenQuestion(
            id = "s1", section = 1, number = 1, marks = 3.0,
            text = "Show that 0.45 is a rational number by writing it in the form p/q in lowest terms.",
            modelAnswer = listOf("0.45 = 45/100", "45/100 = 9/20 (divide top and bottom by 5)", "9 and 20 are integers and 20 ≠ 0, so 0.45 is rational."),
            rubric = listOf(
                crit("a", "Writes 0.45 as a fraction", 1.0, "Rational means it can be written as p/q.", "Write 0.45 = 45/100.",
                    listOf("45/100"), listOf("9/20")),
                crit("b", "Reduces to lowest terms, 9/20", 1.0, "The question asks for lowest terms.", "Divide top and bottom by 5 to get 9/20.",
                    listOf("9/20")),
                crit("c", "Concludes that it is rational", 1.0, "\"Show that\" needs a closing statement.", "End with: so 0.45 is rational.",
                    listOf("rational", "!irrational", "!not rational")),
            ),
            followUp = "Write 0.36 in the form p/q in lowest terms.", noteId = "n-rational",
        ),
        WrittenQuestion(
            id = "s2", section = 1, number = 2, marks = 3.0,
            text = "Is √40 rational or irrational? Give a reason, and write √40 in simplest form.",
            modelAnswer = listOf("40 is not a perfect square (6² = 36 and 7² = 49), so √40 is irrational.", "√40 = √(4 × 10) = 2√10"),
            rubric = listOf(
                crit("a", "States that √40 is irrational", 1.0, "The question asks which type of number it is.", "Write: √40 is irrational.",
                    listOf("irrational")),
                crit("b", "Reason: 40 is not a perfect square", 1.0, "A reason is asked for.", "Say that 40 is not a perfect square, since 36 < 40 < 49.",
                    listOf("not a perfect square"), listOf("not perfect square"), listOf("isn't a perfect square")),
                crit("c", "Simplest form 2√10", 1.0, "The question asks for the simplest form.", "√40 = √(4 × 10) = 2√10.",
                    listOf("2√10")),
            ),
            followUp = "Is √45 rational or irrational? Write it in simplest form.", noteId = "n-irrational",
        ),
        WrittenQuestion(
            id = "s3", section = 1, number = 3, marks = 3.0,
            text = "Express 0.363636… (36 recurring) as a fraction in lowest terms.",
            modelAnswer = listOf("Let x = 0.363636…", "100x = 36.363636…", "Subtract: 99x = 36", "x = 36/99 = 4/11"),
            rubric = listOf(
                crit("a", "Multiplies by 100 to shift one repeating block", 1.0, "Two digits repeat, so the shift must be two places.", "Write 100x = 36.3636…",
                    listOf("100x")),
                crit("b", "Subtracts to get 99x = 36", 1.0, "Subtracting removes the repeating part.", "100x − x = 36, so 99x = 36.",
                    listOf("99x=36"), listOf("36/99")),
                crit("c", "Answer 4/11", 1.0, "The fraction must be in lowest terms.", "36/99 = 4/11 after dividing by 9.",
                    listOf("4/11")),
            ),
            followUp = "Express 0.272727… as a fraction in lowest terms.", noteId = "n-recurring-fraction",
        ),
        WrittenQuestion(
            id = "s4", section = 1, number = 4, marks = 3.0,
            text = "Without dividing, explain why 7/40 has a terminating decimal.",
            modelAnswer = listOf("7/40 is already in lowest terms.", "40 = 2 × 2 × 2 × 5",
                "The only prime factors of the denominator are 2 and 5, so the decimal terminates (7/40 = 0.175)."),
            rubric = listOf(
                crit("a", "Factorises the denominator: 40 = 2 × 2 × 2 × 5", 1.0, "The test uses the prime factors of the denominator.", "Write 40 = 2 × 2 × 2 × 5.",
                    listOf("2*2*2*5"), listOf("2³*5"), listOf("8*5")),
                crit("b", "States that the only prime factors are 2 and 5", 1.0, "This is the rule that decides it.", "Say the only prime factors are 2 and 5.",
                    listOf("2 and 5"), listOf("2s and 5s"), listOf("2, 5"), listOf("2 or 5")),
                crit("c", "Concludes that the decimal terminates", 1.0, "The explanation needs its conclusion.", "End with: so the decimal terminates.",
                    listOf("p:terminat"), listOf("0.175")),
            ),
            followUp = "Without dividing, explain why 5/12 has a recurring decimal.", noteId = "n-decimals",
        ),
        WrittenQuestion(
            id = "s5", section = 1, number = 5, marks = 3.0,
            text = "Name the property shown by 2(5 + 4) = 2·5 + 2·4, and verify it by working out both sides.",
            modelAnswer = listOf("This is the distributive property of multiplication over addition.",
                "Left side: 2(5 + 4) = 2 × 9 = 18", "Right side: 2·5 + 2·4 = 10 + 8 = 18", "Both sides equal 18."),
            rubric = listOf(
                crit("a", "Names the distributive property", 1.0, "The question asks for the name.", "Write: distributive property.",
                    listOf("distributive")),
                crit("b", "Left side: 2 × 9 = 18", 1.0, "Verifying means evaluating each side.", "Add in the bracket first: 2 × 9 = 18.",
                    listOf("2*9"), listOf("2(9)"), listOf("2x9")),
                crit("c", "Right side: 10 + 8 = 18", 1.0, "Verifying means evaluating each side.", "Multiply first: 10 + 8 = 18.",
                    listOf("10+8")),
            ),
            followUp = "Name the property shown by (3 + 8) + 2 = 3 + (8 + 2).", noteId = "n-distributive",
        ),
        WrittenQuestion(
            id = "s6", section = 1, number = 6, marks = 3.0,
            text = "Find the additive inverse and the multiplicative inverse of −4/7. Check each one.",
            modelAnswer = listOf("Additive inverse = 4/7, because −4/7 + 4/7 = 0", "Multiplicative inverse = −7/4, because (−4/7) × (−7/4) = 1"),
            rubric = listOf(
                crit("a", "Additive inverse 4/7", 1.0, "The additive inverse must add to the number to give 0.", "Change the sign: 4/7.",
                    listOf("inverse=4/7"), listOf("inverse is 4/7"), listOf("inverse is +4/7"), listOf("+4/7")),
                crit("b", "Multiplicative inverse −7/4", 1.0, "The multiplicative inverse must multiply with the number to give 1.", "Flip the fraction and keep the sign: −7/4.",
                    listOf("-7/4")),
                crit("c", "Checks: the sum is 0 and the product is 1", 1.0, "The question says to check each one.", "Show −4/7 + 4/7 = 0 and (−4/7) × (−7/4) = 1.",
                    listOf("=0", "=1")),
            ),
            followUp = "Find both inverses of 5/3.", noteId = "n-identity-inverse",
        ),
        WrittenQuestion(
            id = "s7", section = 1, number = 7, marks = 3.0,
            text = "Simplify √98 + √32 − √50.",
            modelAnswer = listOf("√98 = √(49 × 2) = 7√2", "√32 = √(16 × 2) = 4√2", "√50 = √(25 × 2) = 5√2", "7√2 + 4√2 − 5√2 = 6√2"),
            rubric = listOf(
                crit("a", "√98 = 7√2", 1.0, "Each root must be simplified before combining.", "98 = 49 × 2, so √98 = 7√2.",
                    listOf("7√2")),
                crit("b", "√32 = 4√2 and √50 = 5√2", 1.0, "Each root must be simplified before combining.", "32 = 16 × 2 and 50 = 25 × 2.",
                    listOf("4√2", "5√2")),
                crit("c", "Answer 6√2", 1.0, "Like surds combine like like terms.", "7√2 + 4√2 − 5√2 = 6√2.",
                    listOf("6√2")),
            ),
            followUp = "Simplify √27 + √12.", noteId = "n-surds",
        ),
        WrittenQuestion(
            id = "s8", section = 1, number = 8, marks = 3.0,
            text = "Show that 3 + √5 lies between 5 and 6.",
            modelAnswer = listOf("4 < 5 < 9, so √4 < √5 < √9", "That means 2 < √5 < 3", "Add 3 to each part: 5 < 3 + √5 < 6"),
            rubric = listOf(
                crit("a", "Places 5 between the perfect squares 4 and 9", 1.0, "Perfect squares on each side trap the square root.", "Write 4 < 5 < 9.",
                    listOf("4<5<9"), listOf("2²<5<3²"), listOf("√4<√5<√9")),
                crit("b", "Deduces 2 < √5 < 3", 1.0, "Taking square roots keeps the order.", "So 2 < √5 < 3.",
                    listOf("2<√5<3"), listOf("between 2 and 3"), listOf("2.23~"), listOf("2.24")),
                crit("c", "Adds 3 to reach 5 < 3 + √5 < 6", 1.0, "The question is about 3 + √5, not √5.", "Add 3 to every part.",
                    listOf("5<3+√5<6"), listOf("5.23~"), listOf("5.24")),
            ),
            followUp = "Show that 2 + √3 lies between 3 and 4.", noteId = "n-irrational",
        ),
    )

    private val longQuestions = listOf(
        WrittenQuestion(
            id = "l1", section = 2, number = 1, marks = 6.0,
            text = "Prove that √2 is an irrational number.",
            modelAnswer = listOf(
                "Assume √2 is rational, so √2 = p/q where p and q are integers with no common factor and q ≠ 0.",
                "Squaring: 2 = p²/q², so p² = 2q²",
                "So p² is even, which means p is even. Let p = 2k",
                "Then (2k)² = 2q², so 4k² = 2q² and q² = 2k²",
                "So q² is even, which means q is even",
                "p and q are both even, so they have the common factor 2. This contradicts our assumption.",
                "Therefore √2 is irrational.",
            ),
            rubric = listOf(
                crit("a", "Assumes √2 = p/q in lowest terms", 1.0, "A proof by contradiction starts by assuming the opposite.", "Write: assume √2 = p/q with no common factor.",
                    listOf("√2=p/q"), listOf("√2=a/b")),
                crit("b", "Squares to get p² = 2q²", 1.0, "Squaring removes the root.", "2 = p²/q², so p² = 2q².",
                    listOf("p²=2q²"), listOf("a²=2b²")),
                crit("c", "Deduces that p is even", 1.0, "An even square comes only from an even number.", "p² is even, so p is even: p = 2k.",
                    listOf("p is even"), listOf("a is even"), listOf("p=2k"), listOf("p=2m"), listOf("a=2k"), listOf("a=2m")),
                crit("d", "Substitutes to get q² = 2k²", 1.0, "This shows q² is even as well.", "(2k)² = 2q² gives q² = 2k².",
                    listOf("q²=2k²"), listOf("b²=2k²"), listOf("q²=2m²"), listOf("b²=2m²")),
                crit("e", "Deduces that q is even, so p and q share the factor 2", 1.0, "This is what breaks the assumption.", "q² is even, so q is even too.",
                    listOf("q is even"), listOf("b is even"), listOf("both even"), listOf("common factor 2")),
                crit("f", "States the contradiction and concludes", 1.0, "The proof must say what is contradicted.", "Say: this contradicts the assumption, so √2 is irrational.",
                    listOf("p:contradict")),
            ),
            followUp = "Use the same method to prove that √3 is irrational.", noteId = "n-proof-root2",
        ),
        WrittenQuestion(
            id = "l2", section = 2, number = 2, marks = 6.0,
            text = "(a) Express 0.2777… (7 recurring) as a fraction in lowest terms. (b) State, with a reason, whether its decimal is terminating or recurring.",
            modelAnswer = listOf(
                "Let x = 0.2777…", "10x = 2.777…", "100x = 27.777…", "Subtract: 90x = 25", "x = 25/90 = 5/18",
                "The decimal is recurring, because 18 = 2 × 3 × 3 has the prime factor 3.",
            ),
            rubric = listOf(
                crit("a", "Writes 10x = 2.777…", 1.0, "The non-repeating digit must be moved past the point first.", "Multiply by 10: 10x = 2.777…",
                    listOf("10x=2.7~")),
                crit("b", "Writes 100x = 27.777…", 1.0, "A second equation with the same repeating tail is needed.", "Multiply by 100: 100x = 27.777…",
                    listOf("100x=27.7~")),
                crit("c", "Subtracts to get 90x = 25", 1.0, "Subtracting cancels the repeating part.", "100x − 10x = 25, so 90x = 25.",
                    listOf("90x=25"), listOf("25/90")),
                crit("d", "Answer 5/18", 1.0, "The fraction must be in lowest terms.", "25/90 = 5/18 after dividing by 5.",
                    listOf("5/18")),
                crit("e", "States that the decimal is recurring", 1.0, "Part (b) asks which type it is.", "Write: it is recurring.",
                    listOf("p:recur"), listOf("p:repeat")),
                crit("f", "Reason: the denominator 18 has the prime factor 3", 1.0, "Part (b) asks for a reason.", "18 = 2 × 3 × 3 has a prime factor other than 2 and 5.",
                    listOf("2*3*3"), listOf("2*3²"), listOf("factor 3"), listOf("factor of 3"), listOf("3 is a factor")),
            ),
            followUp = "Express 0.1666… as a fraction in lowest terms.", noteId = "n-recurring-fraction",
        ),
        WrittenQuestion(
            id = "l3", section = 2, number = 3, marks = 6.0,
            text = "(a) Simplify (√3 + √2)(√3 − √2). (b) Hence write 1/(√3 + √2) with a rational denominator. (c) Is your answer to (b) rational or irrational?",
            modelAnswer = listOf(
                "(a) (√3 + √2)(√3 − √2) = (√3)² − (√2)² = 3 − 2 = 1",
                "(b) 1/(√3 + √2) = 1/(√3 + √2) × (√3 − √2)/(√3 − √2) = (√3 − √2)/1 = √3 − √2",
                "(c) √3 − √2 is irrational.",
            ),
            rubric = listOf(
                crit("a", "Uses (a + b)(a − b) = a² − b²", 1.0, "This identity removes the roots.", "(√3)² − (√2)² = 3 − 2.",
                    listOf("(√3)²-(√2)²"), listOf("3-2")),
                crit("b", "Gets 1 for part (a)", 1.0, "Part (a) needs its final value.", "3 − 2 = 1.",
                    listOf("=1")),
                crit("c", "Multiplies top and bottom by √3 − √2", 2.0, "Multiplying by the conjugate makes the denominator rational.", "Multiply by (√3 − √2)/(√3 − √2).",
                    listOf("*(√3-√2)"), listOf("(√3-√2)/(√3-√2)"), listOf("conjugate"), listOf("by (√3-√2)"), listOf("by √3-√2")),
                crit("d", "Result √3 − √2", 1.0, "Part (b) needs its final form.", "The denominator becomes 1, leaving √3 − √2.",
                    listOf("=√3-√2")),
                crit("e", "States that the result is irrational", 1.0, "Part (c) asks for the type of number.", "Write: √3 − √2 is irrational.",
                    listOf("irrational")),
            ),
            followUp = "Write 1/(√5 − 2) with a rational denominator.", noteId = "n-surds",
        ),
    )

    val chapterTest = PaperContent(CHAPTER_TEST, paperMcqs, shortQuestions + longQuestions)
}
