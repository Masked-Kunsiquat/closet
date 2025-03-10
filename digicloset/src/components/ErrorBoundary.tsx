import { Component, ReactNode } from "react";
import ErrorDisplay from "./ErrorDisplay";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string;
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
      return <ErrorDisplay message={this.state.errorMessage} mode="alert" onDismiss={() => this.setState({ hasError: false })} />;
    }
    return this.props.children;
  }
}

export default ErrorBoundary;
