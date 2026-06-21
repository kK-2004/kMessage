export function maskValue(value = "") {
  if (!value) return "-";
  if (value.length <= 12) return value;
  return `${value.slice(0, 6)}...${value.slice(-4)}`;
}
