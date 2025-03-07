import { Alert } from "flowbite-react";

type AlertProps = {
  type: "info" | "success" | "error" | "warning" | "dark";
  message: string;
  onClose?: () => void;
};

export default function AppAlert({ type, message, onClose }: AlertProps) {
  return (
    <Alert color={type} onDismiss={onClose}>
      {message}
    </Alert>
  );
}
