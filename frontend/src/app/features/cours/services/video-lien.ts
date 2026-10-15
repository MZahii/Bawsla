/**
 * Lien vidéo d'un chapitre : YouTube (watch, embed, youtu.be) ou fichier .mp4. Le backend n'accepte que ces
 * formes en http(s). On n'intègre jamais l'URL telle quelle : pour YouTube on reconstruit l'adresse d'intégration
 * à partir de l'identifiant seul.
 */
export type LienVideo = { type: 'youtube'; embed: string } | { type: 'mp4'; url: string };

const YOUTUBE = /^https?:\/\/(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?\S*?v=|embed\/)|youtu\.be\/)([\w-]{6,})/i;
const MP4 = /^https?:\/\/[^\s?#]+\.mp4(?:\?\S*)?(?:#\S*)?$/i;

export function analyserLienVideo(url: string | null | undefined): LienVideo | null {
  if (!url) return null;
  const youtube = YOUTUBE.exec(url);
  if (youtube) return { type: 'youtube', embed: `https://www.youtube-nocookie.com/embed/${youtube[1]}` };
  if (MP4.test(url)) return { type: 'mp4', url };
  return null;
}
