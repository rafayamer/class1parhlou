// Types for the type check in CI (tsc doesn't know Deno). Only what the gateway uses.
declare const Deno: {
  env: { get(key: string): string | undefined };
  serve(handler: (request: Request) => Response | Promise<Response>): void;
};
declare module "jsr:@supabase/supabase-js@2" {
  export function createClient(url: string, key: string, options?: unknown): any;
}
