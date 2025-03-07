type Props = {
    onEdit: () => void;
    onDelete: () => void;
  };
  
  export default function ClothingActions({ onEdit, onDelete }: Props) {
    return (
      <div className="flex items-center space-x-4">
        <button
          type="button"
          onClick={onEdit}
          className="text-white bg-primary-700 hover:bg-primary-800 font-medium rounded-lg text-sm px-5 py-2.5 dark:bg-primary-600 dark:hover:bg-primary-700"
        >
          ✏ Edit
        </button>
  
        <button
          type="button"
          onClick={onDelete}
          className="text-white bg-red-600 hover:bg-red-700 font-medium rounded-lg text-sm px-5 py-2.5 dark:bg-red-500 dark:hover:bg-red-600"
        >
          🗑 Delete
        </button>
      </div>
    );
  }
  