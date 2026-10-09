/**
 * Minimal line icons (Lucide, ISC license) behind stable app-level names, so screens never
 * import icon components directly. `glow` draws a soft radial halo in the same color behind it.
 */
import {
  Activity,
  Bed,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Code,
  Copy,
  Database,
  Droplet,
  Dumbbell,
  Flame,
  Footprints,
  HandHeart,
  HeartPulse,
  Info,
  Languages,
  Layers,
  LayoutDashboard,
  LayoutGrid,
  Lightbulb,
  Menu,
  Minus,
  GlassWater,
  CupSoda,
  BatteryCharging,
  Bell,
  RotateCcw,
  Moon,
  Palette,
  Pencil,
  PersonStanding,
  Plus,
  RefreshCw,
  Ribbon,
  Ruler,
  Salad,
  ScanBarcode,
  Scale,
  Search,
  Star,
  Sun,
  Target,
  Trash2,
  User,
  Utensils,
  Wind,
  X,
  type LucideIcon,
} from 'lucide-react-native';
import { useState } from 'react';
import { View } from 'react-native';
import Svg, { Circle, Defs, RadialGradient, Stop } from 'react-native-svg';

const ICONS = {
  overview: LayoutDashboard,
  tips: Lightbulb,
  more: Menu,
  summary: LayoutGrid,
  nutrition: Utensils,
  water: Droplet,
  activity: Footprints,
  heart: HeartPulse,
  body: Scale,
  workout: Dumbbell,
  plus: Plus,
  minus: Minus,
  glass: GlassWater,
  drink: CupSoda,
  battery: BatteryCharging,
  bell: Bell,
  restart: RotateCcw,
  close: X,
  info: Info,
  left: ChevronLeft,
  right: ChevronRight,
  down: ChevronDown,
  up: ChevronUp,
  star: Star,
  search: Search,
  barcode: ScanBarcode,
  trash: Trash2,
  edit: Pencil,
  copy: Copy,
  refresh: RefreshCw,
  flame: Flame,
  pulse: Activity,
  ruler: Ruler,
  user: User,
  target: Target,
  palette: Palette,
  language: Languages,
  layers: Layers,
  database: Database,
  donate: HandHeart,
  code: Code,
  // Tip categories
  air: Wind,
  cancer: Ribbon,
  sun: Sun,
  back: PersonStanding,
  sleep: Moon,
  bed: Bed,
  salad: Salad,
} satisfies Record<string, LucideIcon>;

export type IconName = keyof typeof ICONS;

/**
 * Soft radial glow drawn behind an icon. It is a separate, larger SVG positioned around the
 * icon, so nothing gets clipped to the icon's square bounds.
 */
function Glow({ size, color }: { size: number; color: string }) {
  const g = size * 2;
  const [id] = useState(() => `glow${Math.random().toString(36).slice(2, 8)}`);
  return (
    <Svg width={g} height={g} style={{ position: 'absolute', left: -size / 2, top: -size / 2 }} pointerEvents="none">
      <Defs>
        <RadialGradient id={id} cx="50%" cy="50%" r="50%">
          <Stop offset="0" stopColor={color} stopOpacity={0.35} />
          <Stop offset="0.55" stopColor={color} stopOpacity={0.12} />
          <Stop offset="1" stopColor={color} stopOpacity={0} />
        </RadialGradient>
      </Defs>
      <Circle cx={g / 2} cy={g / 2} r={g / 2} fill={`url(#${id})`} />
    </Svg>
  );
}

export function Icon({
  name,
  size = 22,
  color,
  glow,
  strokeWidth = 2,
}: {
  name: IconName;
  size?: number;
  color: string;
  glow?: boolean;
  strokeWidth?: number;
}) {
  const Cmp = ICONS[name];
  if (!glow) return <Cmp size={size} color={color} strokeWidth={strokeWidth} />;
  return (
    <View style={{ width: size, height: size, overflow: 'visible' }}>
      <Glow size={size} color={color} />
      <Cmp size={size} color={color} strokeWidth={strokeWidth} />
    </View>
  );
}
