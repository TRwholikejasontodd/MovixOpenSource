import React, { useEffect, useMemo, useRef } from 'react';
import { Animated, NativeModules, Platform, StyleSheet, Text, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';

// An original vector MYO logo assembly, adapted to portrait iPhone displays.
// Fragments converge to *one* fixed geometry; no random fonts or camera shifts.
// Android TV uses a similar geometric signature. The audio is an exact 4.056s
// approved asset played by a tiny native iOS AVAudioPlayer bridge.
const PIECES = [
  { d: 'M 0 0 L 22 0 L 60 54 L 60 82 L 25 39 L 25 96 L 0 96 Z', x: -95, y: -34, rot: '-14deg', delay: 580, duration: 900 },
  { d: 'M 60 54 L 95 0 L 118 0 L 118 96 L 93 96 L 93 39 L 60 82 Z', x: 108, y: 55, rot: '12deg', delay: 730, duration: 850 },
  { d: 'M 143 0 L 173 0 L 199 40 L 199 69 L 187 60 Z', x: -62, y: -95, rot: '-15deg', delay: 890, duration: 850 },
  { d: 'M 255 0 L 225 0 L 199 40 L 199 69 L 212 60 Z', x: 89, y: -67, rot: '17deg', delay: 980, duration: 850 },
  { d: 'M 187 57 L 212 57 L 212 96 L 187 96 Z', x: 0, y: 85, rot: '0deg', delay: 1110, duration: 860 },
  { d: 'M 307 1 A 47 47 0 0 1 354 48', x: 70, y: -44, rot: '24deg', delay: 1190, duration: 820, stroke: true },
  { d: 'M 354 48 A 47 47 0 0 1 307 95', x: 72, y: 42, rot: '-18deg', delay: 1280, duration: 820, stroke: true },
  { d: 'M 307 95 A 47 47 0 0 1 260 48', x: -68, y: 54, rot: '14deg', delay: 1370, duration: 820, stroke: true },
  { d: 'M 260 48 A 47 47 0 0 1 307 1', x: -72, y: -47, rot: '-15deg', delay: 1460, duration: 820, stroke: true },
] as const;

const TITLES = [
  'le Vénérable', 'le Brave', 'le Grand', 'le Sage',
  'le Visionnaire', 'le Magnifique', 'le Lumineux', 'le Légendaire',
];
const MYO_COLOR = '#A576F6';
const LENGTH_MS = 4200;

type Props = { onFinish: () => void };

export default function MyoPremiumIntro({ onFinish }: Props) {
  const values = useRef(PIECES.map(() => new Animated.Value(0))).current;
  const credit = useRef(new Animated.Value(0)).current;
  const latestFinish = useRef(onFinish);
  latestFinish.current = onFinish;
  const title = useMemo(() => TITLES[Math.floor(Math.random() * TITLES.length)], []);

  useEffect(() => {
    if (Platform.OS === 'ios') {
      NativeModules.PlaybackAwake?.playMyoIntro?.();
    }
    const ongoing = values.map((value, i) => {
      const piece = PIECES[i];
      const animation = Animated.timing(value, {
        toValue: 1,
        delay: piece.delay,
        duration: piece.duration,
        useNativeDriver: true,
      });
      animation.start();
      return animation;
    });
    const creditAnim = Animated.timing(credit, {
      toValue: 1, delay: 3470, duration: 350, useNativeDriver: true,
    });
    creditAnim.start();
    const done = setTimeout(() => latestFinish.current(), LENGTH_MS);
    return () => {
      clearTimeout(done);
      for (const animation of ongoing) animation.stop();
      creditAnim.stop();
      if (Platform.OS === 'ios') NativeModules.PlaybackAwake?.stopMyoIntro?.();
    };
  }, [credit, values]);

  return (
    <View style={styles.screen} accessible accessibilityLabel="MYO Premium, introduction">
      <View style={styles.backgroundHalo} pointerEvents="none" />
      <View style={styles.logoFrame}>
        {PIECES.map((piece, i) => {
          const phase = values[i];
          const isStroke = 'stroke' in piece && piece.stroke;
          return (
            <Animated.View
              key={i}
              pointerEvents="none"
              style={[
                styles.fragment,
                {
                  opacity: phase,
                  transform: [
                    { translateX: phase.interpolate({ inputRange: [0, 1], outputRange: [piece.x, 0] }) },
                    { translateY: phase.interpolate({ inputRange: [0, 1], outputRange: [piece.y, 0] }) },
                    { rotate: phase.interpolate({ inputRange: [0, 1], outputRange: [piece.rot, '0deg'] }) },
                  ],
                },
              ]}
            >
              <Svg width="100%" height="100%" viewBox="0 0 354 105">
                <Path
                  d={piece.d}
                  fill={isStroke ? 'none' : MYO_COLOR}
                  stroke={isStroke ? MYO_COLOR : 'none'}
                  strokeWidth={isStroke ? 23 : 0}
                  strokeLinecap="butt"
                />
              </Svg>
            </Animated.View>
          );
        })}
      </View>
      <Animated.View pointerEvents="none" style={[styles.credit, { opacity: credit }]}>
        <Text style={styles.creditText}>Développé par Louis Hedouin {title}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 100,
    backgroundColor: '#08060F',
    alignItems: 'center',
    justifyContent: 'center',
  },
  backgroundHalo: {
    position: 'absolute',
    top: '29%',
    left: '5%',
    width: '90%',
    height: '36%',
    backgroundColor: '#170B2F',
    borderRadius: 160,
    opacity: .30,
  },
  logoFrame: {
    width: '82%',
    aspectRatio: 354 / 105,
    maxWidth: 354,
  },
  fragment: {
    ...StyleSheet.absoluteFillObject,
  },
  credit: {
    position: 'absolute',
    top: '58%',
    alignItems: 'center',
    width: '94%',
  },
  creditText: {
    color: '#AAA3B4',
    fontSize: 9,
    letterSpacing: .2,
    textAlign: 'center',
    opacity: .52,
  },
});
