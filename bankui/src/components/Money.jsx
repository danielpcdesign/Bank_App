/*
 * A currency amount.
 *
 * A component rather than a formatMoney() helper, because there are two decisions here and
 * only one of them is text. The other is what an overdrawn balance LOOKS like, which is
 * markup and a class - so putting the pair in a component keeps them from drifting apart,
 * and means no caller can format a balance while forgetting to mark it as negative.
 *
 * WHY NOT {account.balance}. Because 1234.5 renders as "1234.5", which is not money: no
 * thousands separator, no symbol, and a stray single decimal place that reads as a bug.
 * Intl.NumberFormat is the browser's own currency formatter and knows the conventions the
 * reader expects - where the symbol goes, which separator groups the digits.
 *
 * NO DECIMAL PLACES, deliberately. Balances are `double` on the back end and every amount in
 * this system is a whole number, so the default two fraction digits would render 1234 as
 * "$1,234.00" - advertising a precision to the cent that the app cannot actually hold. The
 * zeroes would be decoration, and decoration on a balance is a lie. (The whole-number rule
 * is also what makes `double` safe here at all: a double stores every integer exactly up to
 * 2^53, and the 0.1 + 0.2 problem only bites on fractional parts.)
 *
 * Constructed ONCE at module scope. Intl.NumberFormat is expensive to build - it loads
 * locale data - and cheap to reuse. Building it inside the component would do that work on
 * every render of every row.
 *
 * locale `undefined` means "the reader's locale" while the currency stays fixed. That pair
 * is deliberate: the amount IS dollars regardless of who is looking, but whether the
 * thousands separator is a comma or a space is a fact about the reader, not about the money.
 *
 * The currency code is a FRONT-END ASSUMPTION. Nothing in the domain has picked one - Phase
 * 1 stores bare doubles with no unit - so this is the single line to change when it does.
 */
const CURRENCY = 'USD'

const format = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: CURRENCY,
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
  // accounting notation: -100 renders as ($100) rather than -$100. It is the convention on
  // a bank statement, and more usefully it is a SECOND signal that the balance is negative,
  // independent of colour - which matters because a minus sign is one glyph wide and easy to
  // miss, and because colour is the signal a colour-blind reader may not receive at all.
  currencySign: 'accounting',
})

export default function Money({ amount }) {

  // A missing or unparseable amount is a real possibility while the back end is still being
  // written - a renamed field arrives here as undefined, and Intl would cheerfully print
  // "$NaN". An em dash says "no value" without pretending to be one.
  if (!Number.isFinite(amount)) {
    return <span className="muted">&mdash;</span>
  }

  /*
   * A negative balance is NOT an error, and this is the line where that gets decided.
   *
   * A checking account inside its overdraft limit is legitimately below zero - the domain
   * allows it on purpose, and CheckingAccount.withdraw() exists to permit exactly this. So
   * it must not be styled with .error, which is the red this app uses for "something went
   * wrong". It gets its own class and its own colour, and the parentheses above carry the
   * meaning even for a reader who sees no colour at all.
   */
  return (
    <span className={amount < 0 ? 'money money-negative' : 'money'}>
      {format.format(amount)}
    </span>
  )
}
