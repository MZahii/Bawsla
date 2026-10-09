// Point d'entrée des composants partagés (socle). Usage dans un module :
// import { AiBox, Badge, BwButton } from '../../../../shared';
export { AiBox } from './components/ai-box/ai-box';
export { AiLoading } from './components/ai-loading/ai-loading';
export { Badge, type BadgeLevel, type BadgeVariant } from './components/badge/badge';
export { EmptyState } from './components/empty-state/empty-state';
export { Logo, type LogoTheme, type LogoVariant } from './components/logo/logo';
export { StatTile } from './components/stat-tile/stat-tile';
export { BwButton, type ButtonVariant } from './directives/bw-button';
// Composants des écrans : rose des notions, itinéraire, barre de progression.
// La bibliothèque d'interface (graphiques, tableaux de bord, cartes de cours…) est dans ./ui.
export { CompassRose, LIBELLE_MAITRISE, niveauMaitrise, type RoseNotion } from './components/compass-rose/compass-rose';
export { Progress } from './components/progress/progress';
export { Route, type RouteStep } from './components/route/route';
