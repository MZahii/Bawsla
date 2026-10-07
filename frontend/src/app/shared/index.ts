// Point d'entrée des composants partagés (socle). Usage dans un module :
// import { AiBox, Badge, BwButton } from '../../../../shared';
export { AiBox } from './components/ai-box/ai-box';
export { AiLoading } from './components/ai-loading/ai-loading';
export { Badge, type BadgeLevel, type BadgeVariant } from './components/badge/badge';
export { EmptyState } from './components/empty-state/empty-state';
export { Logo, type LogoTheme, type LogoVariant } from './components/logo/logo';
export { StatTile } from './components/stat-tile/stat-tile';
export { BwButton, type ButtonVariant } from './directives/bw-button';
