import { Modal } from "flowbite-react";

interface ConfirmationModalProps {
  isOpen: boolean;
  title?: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
  confirmText?: string;
  cancelText?: string;
}

const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  title = "Confirm Action",
  message,
  onConfirm,
  onCancel,
  confirmText = "Confirm",
  cancelText = "Cancel",
}) => {
  if (!isOpen) return null;

  return (
    <Modal show={isOpen} onClose={onCancel}>
      <Modal.Header>{title}</Modal.Header>
      <Modal.Body>
        <p className="text-gray-700 dark:text-gray-300">{message}</p>
      </Modal.Body>
      <Modal.Footer>
        <button
          onClick={onConfirm}
          className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 focus:ring-4 focus:ring-red-300"
        >
          {confirmText}
        </button>
        <button
          onClick={onCancel}
          className="px-4 py-2 bg-gray-200 dark:bg-gray-700 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 focus:ring-4 focus:ring-gray-300 dark:focus:ring-gray-600"
        >
          {cancelText}
        </button>
      </Modal.Footer>
    </Modal>
  );
};

export default ConfirmationModal;
