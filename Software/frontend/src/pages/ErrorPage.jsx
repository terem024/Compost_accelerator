import { Link, useNavigate } from 'react-router-dom';

const errorContent = {
  notFound: {
    code: '404',
    title: 'Page not found',
    message: 'The page you are looking for may have been moved, deleted, or never existed.',
    hint: 'Check the address bar or return to a working section of the compost dashboard.',
  },
  loadError: {
    code: 'Error',
    title: "Couldn't load page",
    message: 'Something went wrong while displaying this section.',
    hint: 'Try refreshing the page. If the issue continues, return to the dashboard and try again.',
  },
};

function ErrorPage({
  type = 'loadError',
  title,
  message,
  hint,
  details,
  homePath = '/dashboard',
}) {
  const navigate = useNavigate();
  const content = errorContent[type] || errorContent.loadError;

  return (
    <main className="error-page">
      <section className="error-panel" aria-labelledby="error-page-title">
        <div className="error-code">{content.code}</div>
        <h1 id="error-page-title">{title || content.title}</h1>
        <p>{message || content.message}</p>
        <p className="error-hint">{hint || content.hint}</p>

        {details && (
          <details className="error-details">
            <summary>Technical details</summary>
            <pre>{details}</pre>
          </details>
        )}

        <div className="error-actions">
          <button type="button" className="primary-button" onClick={() => navigate(-1)}>
            Go Back
          </button>
          <Link className="secondary-button error-link-button" to={homePath}>
            Go to Dashboard
          </Link>
        </div>
      </section>
    </main>
  );
}

export default ErrorPage;
