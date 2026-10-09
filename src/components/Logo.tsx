/** App logo: a leaf with a heartbeat line — natural health, measured. Mirrors assets/logo.svg. */
import Svg, { Circle, Path } from 'react-native-svg';

export function Logo({ size = 48, color = '#2E9E6A' }: { size?: number; color?: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      <Circle cx={50} cy={50} r={50} fill={color} />
      <Path d="M50 18 C74 26 82 50 70 72 C62 84 44 86 34 78 C22 66 26 34 50 18 Z" fill="#FFFFFF" />
      <Path
        d="M26 56 H40 L45 44 L52 66 L57 52 H74"
        stroke={color}
        strokeWidth={5}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </Svg>
  );
}
