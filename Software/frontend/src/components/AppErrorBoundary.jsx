import { Component } from 'react';
import ErrorPage from '../pages/ErrorPage.jsx';

class AppErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = {
      error: null,
    };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidUpdate(previousProps) {
    if (previousProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  render() {
    if (this.state.error) {
      return (
        <ErrorPage
          type="loadError"
          details={this.state.error.message}
          homePath={this.props.homePath}
        />
      );
    }

    return this.props.children;
  }
}

export default AppErrorBoundary;
