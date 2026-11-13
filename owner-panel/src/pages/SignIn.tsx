import { useState } from "react";
import { db, explain } from "../supabase";

/** Email sign-in: a link arrives by email (and a 6-digit code where the email template shows one). */
export function SignIn() {
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const send = async () => {
    setBusy(true); setMessage("");
    const { error } = await db.auth.signInWithOtp({ email: email.trim(), options: { emailRedirectTo: location.origin + location.pathname } });
    setBusy(false);
    if (error) setMessage(explain(error)); else setSent(true);
  };
  const verify = async () => {
    setBusy(true); setMessage("");
    const { error } = await db.auth.verifyOtp({ email: email.trim(), token: code.trim(), type: "email" });
    setBusy(false);
    if (error) setMessage("That code didn't work. Check it, or use the link in the email.");
  };

  return (
    <div className="center">
      <div className="card">
        <h1>Parhlou owner panel</h1>
        <p className="muted">Sign in with the owner's email. Everything here is checked by the server.</p>
        <label className="field"><span>Email</span>
          <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <button className="primary" disabled={busy || !email.includes("@")} onClick={send}>{sent ? "Send again" : "Email me a sign-in link"}</button>
        {sent && (
          <>
            <p className="ok">Check your email and open the link on this device. If the email shows a code, enter it here.</p>
            <label className="field"><span>Code</span>
              <input inputMode="numeric" autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} />
            </label>
            <button disabled={busy || code.trim().length < 6} onClick={verify}>Sign in with code</button>
          </>
        )}
        {message && <p className="error" role="alert">{message}</p>}
      </div>
    </div>
  );
}
