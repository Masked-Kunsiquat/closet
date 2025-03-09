import { Alert, Toast } from "flowbite-react";
import { HiExclamation, HiX } from "react-icons/hi";

interface ErrorDisplayProps {
  message: string;
  mode: "alert" | "toast";
  onDismiss: () => void;
}

const ErrorDisplay: React.FC<ErrorDisplayProps> = ({ message, mode, onDismiss }) => {
  if (mode === "alert") {
    return (
      <Alert color="failure" icon={HiExclamation} className="my-4">
        <div className="flex justify-between items-center">
          <span className="font-medium">{message}</span>
          <button className="ml-4 text-red-900 font-bold" onClick={onDismiss}>
            ✖
          </button>
        </div>
      </Alert>
    );
  }

  return (
    <Toast className="fixed top-4 right-4 z-50">
      <div className="flex items-center w-full max-w-xs p-4 bg-red-100 border border-red-400 text-red-700 rounded-lg shadow">
        <div className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-red-500 text-white">
          <HiX className="h-5 w-5" />
        </div>
        <div className="ml-3 text-sm font-normal">{message}</div>
        <button className="ml-auto text-red-900 font-bold" onClick={onDismiss}>
          ✖
        </button>
      </div>
    </Toast>
  );
};

export default ErrorDisplay;
