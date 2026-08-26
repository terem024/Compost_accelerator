function NotificationModal({ isOpen, message, type = 'success', onClose }) {
  if (!isOpen) return null;

  const title = type === 'error' ? 'Error' : type === 'info' ? 'Notice' : 'Success';

  return (
    <div
      className="notification-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="notification-modal-title"
      onClick={onClose}
    >
      <div className="notification-modal" onClick={(event) => event.stopPropagation()}>
        <div className={`notification-modal-icon ${type}`} aria-hidden="true">
          {type === 'error' ? '!' : type === 'info' ? 'i' : 'OK'}
        </div>
        <h2 id="notification-modal-title">{title}</h2>
        <p>{message}</p>
        <button type="button" className={`notification-modal-button ${type}`} onClick={onClose}>
          OK
        </button>
      </div>
    </div>
  );
}

export default NotificationModal;
