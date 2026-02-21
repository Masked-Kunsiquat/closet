import { BottomTabBarButtonProps } from '@react-navigation/bottom-tabs';
import { PlatformPressable } from '@react-navigation/elements';
import * as Haptics from 'expo-haptics';

/**
 * Render a bottom-tab pressable that triggers a light haptic on iOS when pressed.
 *
 * The component forwards all received `BottomTabBarButtonProps` to `PlatformPressable`
 * and augments the `onPressIn` handler to play a light impact haptic on iOS before
 * invoking any original `onPressIn`.
 *
 * @param props - Props for the bottom tab button which are passed through to `PlatformPressable`
 * @returns A `PlatformPressable` configured for the bottom tab bar that adds iOS light haptic feedback on press-in
 */
export function HapticTab(props: BottomTabBarButtonProps) {
  return (
    <PlatformPressable
      {...props}
      onPressIn={(ev) => {
        if (process.env.EXPO_OS === 'ios') {
          // Add a soft haptic feedback when pressing down on the tabs.
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        }
        props.onPressIn?.(ev);
      }}
    />
  );
}