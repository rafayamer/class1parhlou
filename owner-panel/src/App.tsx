import { useEffect, useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { db, explain } from "./supabase";
import { SignIn } from "./pages/SignIn";
import { Dashboard } from "./pages/Dashboard";
import { Content } from "./pages/Content";
import { Settings } from "./pages/Settings";
import { Learners } from "./pages/Learners";
import { AiLog } from "./pages/AiLog";

const PAGES = [
  ["dashboard", "Dashboard"],
  ["content", "Content"],
  ["settings", "AI settings"],
  ["learners", "Learners"],
  ["ai-log", "AI log"],
] as const;
type Page = (typeof PAGES)[number][0];

const pageFromHash = (): Page => {
  const h = location.hash.replace(/^#\/?/, "");
  return (PAGES.find(([id]) => id === h)?.[0] ?? "dashboard") as Page;
};

export function App() {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const [owner, setOwner] = useState<"checking" | "yes" | "no">("checking");
  const [problem, setProblem] = useState("");
  const [page, setPage] = useState<Page>(pageFromHash());

  useEffect(() => {
    db.auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = db.auth.onAuthStateChange((_e, s) => setSession(s));
    const onHash = () => setPage(pageFromHash());
    addEventListener("hashchange", onHash);
    return () => { data.subscription.unsubscribe(); removeEventListener("hashchange", onHash); };
  }, []);

  // The database decides who the owner is; the page itself grants nothing.
  useEffect(() => {
    if (!session) return;
    setOwner("checking");
    db.rpc("owner_dashboard").then(({ error }) => {
      if (!error) setOwner("yes");
      else { setOwner("no"); setProblem(explain(error)); }
    });
  }, [session?.user.id]);

  if (session === undefined) return <div className="center muted">Loading…</div>;
  if (!session) return <SignIn />;
  if (owner === "checking") return <div className="center muted">Checking your account…</div>;
  if (owner === "no") {
    return (
      <div className="center card">
        <h1>Not the owner account</h1>
        <p>{problem}</p>
        <p className="muted small">Signed in as {session.user.email}. Only the owner's email can use this panel.</p>
        <button onClick={() => db.auth.signOut()}>Sign out</button>
      </div>
    );
  }
  return (
    <div className="shell">
      <nav className="nav" aria-label="Sections">
        <div className="brand">Parhlou · Owner</div>
        {PAGES.map(([id, label]) => (
          <a key={id} href={`#/${id}`} className={page === id ? "active" : ""} aria-current={page === id ? "page" : undefined}>{label}</a>
        ))}
        <div className="who">
          <div>{session.user.email}</div>
          <button style={{ marginTop: 8 }} onClick={() => db.auth.signOut()}>Sign out</button>
        </div>
      </nav>
      <main>
        {page === "dashboard" && <Dashboard />}
        {page === "content" && <Content />}
        {page === "settings" && <Settings />}
        {page === "learners" && <Learners />}
        {page === "ai-log" && <AiLog />}
      </main>
    </div>
  );
}
