import { Component, ReactNode } from "react";
import ErrorDisplay from "./ErrorDisplay";
import { ErrorBoundaryState } from "../types";

interface ErrorBoundaryProps {
  children: ReactNode;
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  private timeoutId: NodeJS.Timeout | null = null; // Store timeout ID

  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, errorMessage: "" };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, errorMessage: error.message };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("❌ Uncaught error:", error, errorInfo);
  }

  componentWillUnmount() {
    // ✅ Prevent memory leak by clearing timeout when component unmounts
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
  }

  handleDismiss = () => {
    this.setState({ hasError: false, errorMessage: "" });
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
  };

  render() {
    if (this.state.hasError) {
      return (
        <ErrorDisplay
          errors={[
            {
              id: 1,
              message: this.state.errorMessage,
              mode: "alert",
            },
          ]}
          onDismiss={this.handleDismiss}
        />
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
