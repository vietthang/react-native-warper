import { NativeModules, requireNativeComponent } from "react-native";

export const Warper = NativeModules.Warper;

/**
 * Composes `View`.
 *
 * - src: string
 * - borderRadius: number
 * - resizeMode: 'cover' | 'contain' | 'stretch'
 */
export const WarpView = requireNativeComponent("WarpView");
