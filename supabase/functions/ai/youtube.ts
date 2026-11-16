// YouTube lesson search for Ask Ustaad (YouTube Data API v3). Results are links to YouTube's own player, never re-hosted.
// Pure TypeScript with an injected fetch, so it runs in the Edge Function and in tests.
import type { Video } from "./core.ts";

export type GetJson = (url: string) => Promise<{ ok: boolean; json(): Promise<any> }>;

/** Up to [max] safe, education-category, medium-length lesson videos for a topic query. Empty on any problem. */
export async function searchVideos(query: string, key: string, get: GetJson, max = 3): Promise<Video[]> {
  const q = query.trim();
  if (!key || !q) return [];
  const params = new URLSearchParams({
    part: "snippet", type: "video", maxResults: "8", q, key,
    safeSearch: "strict", videoEmbeddable: "true", videoCategoryId: "27", regionCode: "PK", relevanceLanguage: "en", videoDuration: "medium",
  });
  try {
    const res = await get(`https://www.googleapis.com/youtube/v3/search?${params}`);
    if (!res.ok) return [];
    const data = await res.json();
    return pickVideos(data, max);
  } catch {
    return [];
  }
}

/** Keeps proper lessons: drops shorts, live streams and clickbait-looking titles. */
export function pickVideos(data: any, max = 3): Video[] {
  const items: any[] = Array.isArray(data?.items) ? data.items : [];
  return items
    .filter((it) => it?.id?.videoId && it?.snippet?.title && it.snippet.liveBroadcastContent === "none")
    .filter((it) => !/#shorts|\bshorts?\b|prank|reaction/i.test(it.snippet.title))
    .slice(0, max)
    .map((it) => ({
      id: String(it.id.videoId), title: decode(String(it.snippet.title)), channel: decode(String(it.snippet.channelTitle ?? "")),
      thumbnail: String(it.snippet.thumbnails?.medium?.url ?? it.snippet.thumbnails?.default?.url ?? ""),
    }));
}

const decode = (s: string) => s.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
