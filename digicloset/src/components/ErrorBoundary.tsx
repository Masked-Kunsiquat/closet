import { Component, ReactNode } from "react";
import ErrorDisplay from "./ErrorDisplay";
import { ErrorBoundaryState } from "../types";

interface ErrorBoundaryProps {
  children: ReactNode;
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
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
          onDismiss={() => this.setState({ hasError: false, errorMessage: "" })}
        />
      );
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
