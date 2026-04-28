interface TextMessageProps {
  content: string
}

export default function TextMessage({ content }: TextMessageProps) {
  return <p className="break-words">{content}</p>
}
