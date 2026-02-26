import React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { FontSize, FontWeight, Palette, Radius, Spacing } from '@/constants/tokens';

interface Props {
  children: React.ReactNode;
}

interface State {
  hasError: boolean;
  errorMessage: string | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, errorMessage: null };
  }

  static getDerivedStateFromError(error: unknown): State {
    return {
      hasError: true,
      errorMessage: error instanceof Error ? error.message : String(error),
    };
  }

  componentDidCatch(error: unknown, info: React.ErrorInfo) {
    if (__DEV__) {
      console.error('[ErrorBoundary] unhandled render error', error, info.componentStack);
    }
  }

  handleReset = () => {
    this.setState({ hasError: false, errorMessage: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <View style={styles.container}>
          <Text style={styles.title}>Something went wrong</Text>
          <Text style={styles.subtitle}>Restart the app to continue.</Text>
          {__DEV__ && this.state.errorMessage ? (
            <Text style={styles.detail}>{this.state.errorMessage}</Text>
          ) : null}
          <TouchableOpacity style={styles.button} onPress={this.handleReset}>
            <Text style={styles.buttonText}>Try again</Text>
          </TouchableOpacity>
        </View>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.surface0,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing[8],
    gap: Spacing[4],
  },
  title: {
    color: Palette.textPrimary,
    fontSize: FontSize.lg,
    fontWeight: FontWeight.semibold,
    textAlign: 'center',
  },
  subtitle: {
    color: Palette.textSecondary,
    fontSize: FontSize.md,
    textAlign: 'center',
  },
  detail: {
    color: Palette.textSecondary,
    fontSize: FontSize.sm,
    textAlign: 'center',
    fontFamily: 'monospace',
  },
  button: {
    marginTop: Spacing[2],
    paddingHorizontal: Spacing[6],
    paddingVertical: Spacing[3],
    borderRadius: Radius.md,
    backgroundColor: Palette.warning,
  },
  buttonText: {
    color: Palette.surface0,
    fontSize: FontSize.md,
    fontWeight: FontWeight.semibold,
  },
});
