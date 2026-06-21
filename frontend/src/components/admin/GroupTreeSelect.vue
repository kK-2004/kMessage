<template>
  <div class="group-tree-select" :class="{ 'group-tree-select--compact': compact }">
    <div v-if="searchable" class="gts-search">
      <svg class="gts-search-icon" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <circle cx="9" cy="9" r="6.5" stroke="currentColor" stroke-width="1.6" />
        <path d="M14 14L18 18" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      </svg>
      <input
        v-model="keyword"
        class="gts-search-input"
        type="text"
        :placeholder="placeholder"
      />
      <button v-if="keyword" class="gts-search-clear" type="button" aria-label="清除" @click="keyword = ''">
        <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
          <path d="M2 2L12 12M12 2L2 12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
      </button>
    </div>

    <div class="gts-tree" role="tree">
      <p v-if="visibleTree.length === 0" class="gts-empty">{{ emptyText }}</p>
      <TreeNode
        v-for="node in visibleTree"
        :key="node.id"
        :node="node"
        :depth="0"
        :keyword="keyword"
        :multiple="multiple"
        :selectable="selectable"
        :selected="selectedKeys"
        :expanded="expandedKeys"
        @toggle-expand="toggleExpand"
        @toggle-select="toggleSelect"
        @sub-create="(n) => emit('sub-create', n)"
        @edit="(n) => emit('edit', n)"
        @delete="(n) => emit('delete', n)"
        @remove-member="(userId) => emit('remove-member', userId)"
      />
    </div>
  </div>
</template>

<script setup>
import { Teleport, computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, ref, useSlots, watch } from "vue";

const props = defineProps({
  data: { type: Array, default: () => [] },
  modelValue: { type: [String, Array], default: "" },
  multiple: { type: Boolean, default: false },
  placeholder: { type: String, default: "搜索分组" },
  emptyText: { type: String, default: "没有匹配的分组" },
  defaultExpandAll: { type: Boolean, default: true },
  compact: { type: Boolean, default: false },
  searchable: { type: Boolean, default: true },
  selectable: { type: Boolean, default: true },
});

const emit = defineEmits(["update:modelValue", "select", "sub-create", "edit", "delete", "remove-member"]);
const slots = useSlots();
const keyword = ref("");
const expandedKeys = ref(new Set());
const selectedKeys = ref(toSelectionSet(props.modelValue));
const popoverOpenId = ref("");

watch(
  () => props.modelValue,
  (value) => {
    selectedKeys.value = toSelectionSet(value);
    // Auto-expand the selected group so its inline member children are visible.
    const selectedId = Array.isArray(value) ? value[value.length - 1] : value;
    if (selectedId) {
      const next = new Set(expandedKeys.value);
      next.add(selectedId);
      expandedKeys.value = next;
    }
  }
);

// Track node ids we have already seen so that re-renders (e.g. members injected
// into the selected group, or a renamed group) do NOT clobber the user's manual
// expand/collapse state. Each node can be expanded independently — expanding one
// never collapses another.
const seenIds = ref(new Set());

watch(
  () => props.data,
  (nodes) => {
    // Collect every expandable node with its depth so we can apply the default
    // expand policy only to the right tier.
    const expandable = new Map();
    collectExpandable(nodes, expandable, 0);

    const next = new Set();
    expandable.forEach((info, id) => {
      // Existing nodes keep whatever expand state the user left them in, so each
      // node can be expanded/collapsed without affecting its siblings.
      if (expandedKeys.value.has(id)) {
        next.add(id);
        return;
      }
      // Brand-new nodes inherit the default expand policy exactly once.
      if (!seenIds.value.has(id) && shouldDefaultExpand(info)) {
        next.add(id);
      }
    });
    expandedKeys.value = next;
    seenIds.value = new Set(expandable.keys());
  },
  { immediate: true, deep: true }
);

function toSelectionSet(value) {
  if (Array.isArray(value)) return new Set(value.filter(Boolean));
  return value ? new Set([value]) : new Set();
}

function collectExpandable(nodes, out, depth) {
  nodes.forEach((node) => {
    if (node.children?.length) {
      out.set(node.id, { depth, node });
      collectExpandable(node.children, out, depth + 1);
    }
  });
}

// Default expand policy applied only to newly appeared nodes:
//  - defaultExpandAll=true  -> every expandable node
//  - defaultExpandAll=false -> only top-level (root) nodes with children
function shouldDefaultExpand(info) {
  if (props.defaultExpandAll) return true;
  return info.depth === 0 && Boolean(info.node.children?.length);
}

function toggleExpand(id) {
  const next = new Set(expandedKeys.value);
  next.has(id) ? next.delete(id) : next.add(id);
  expandedKeys.value = next;
}

function toggleSelect(node) {
  if (!props.selectable) return;
  const next = new Set(selectedKeys.value);
  if (props.multiple) {
    next.has(node.id) ? next.delete(node.id) : next.add(node.id);
    selectedKeys.value = next;
    emit("update:modelValue", Array.from(next));
  } else {
    selectedKeys.value = new Set([node.id]);
    emit("update:modelValue", node.id);
  }
  emit("select", node);
}

const visibleTree = computed(() => {
  const term = keyword.value.trim().toLowerCase();
  if (!term) return props.data;

  function filterNodes(nodes) {
    const result = [];
    for (const node of nodes) {
      const childMatches = node.children ? filterNodes(node.children) : [];
      const selfMatch = node.name.toLowerCase().includes(term);
      if (selfMatch || childMatches.length) {
        result.push({
          ...node,
          children: childMatches.length ? childMatches : node.children,
        });
      }
    }
    return result;
  }

  return filterNodes(props.data);
});

const palette = ["#2563eb", "#0f9f6e", "#d97706", "#dc2626", "#7c3aed", "#0891b2", "#be185d"];

function colorFor(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return palette[hash % palette.length];
}

function initials(name) {
  return name.trim().slice(0, 2).toUpperCase();
}

function directMemberCount(node) {
  return (node.children || []).filter((child) => child.kind === "member").length;
}

function ownMemberCount(node, isSelected) {
  const visibleMemberCount = directMemberCount(node);
  if (isSelected || visibleMemberCount) return visibleMemberCount;

  const apiMemberCount = Number(node.memberCount);
  return Number.isFinite(apiMemberCount) ? apiMemberCount : 0;
}

function subtreeMemberCount(node, selected) {
  if (node.kind === "member") return 1;
  return (node.children || []).reduce((sum, child) => {
    if (child.kind === "member") return sum;
    return sum + subtreeMemberCount(child, selected);
  }, ownMemberCount(node, selected.has(node.id)));
}

function highlightedName(name, term) {
  if (!term) return name;
  const index = name.toLowerCase().indexOf(term.toLowerCase());
  if (index === -1) return name;
  return [
    name.slice(0, index),
    h("mark", name.slice(index, index + term.length)),
    name.slice(index + term.length),
  ];
}

const TreeNode = defineComponent({
  name: "TreeNode",
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, required: true },
    keyword: { type: String, default: "" },
    multiple: Boolean,
    selectable: Boolean,
    selected: { type: Set, required: true },
    expanded: { type: Set, required: true },
  },
  emits: ["toggle-expand", "toggle-select", "sub-create", "edit", "delete", "remove-member"],
  setup(nodeProps, { emit: nodeEmit }) {
    const actionButton = ref(null);
    const actionMenu = ref(null);
    const menuPosition = ref({ top: 0, left: 0 });
    const menuWidth = 116;

    function placeMenu() {
      const rect = actionButton.value?.getBoundingClientRect();
      if (!rect) return;
      const gutter = 8;
      const left = Math.min(
        window.innerWidth - menuWidth - gutter,
        Math.max(gutter, rect.right - menuWidth)
      );
      menuPosition.value = {
        top: rect.bottom + 6,
        left,
      };
    }

    async function toggleMenu(event, node) {
      event.stopPropagation();
      if (popoverOpenId.value === node.id) {
        popoverOpenId.value = "";
        return;
      }
      popoverOpenId.value = node.id;
      await nextTick();
      placeMenu();
    }

    function closeMenu() {
      if (popoverOpenId.value === nodeProps.node.id) popoverOpenId.value = "";
    }

    function handleOutsideMouseDown(event) {
      if (popoverOpenId.value !== nodeProps.node.id) return;
      const target = event.target;
      if (actionButton.value?.contains(target) || actionMenu.value?.contains(target)) return;
      closeMenu();
    }

    onMounted(() => {
      document.addEventListener("mousedown", handleOutsideMouseDown);
      window.addEventListener("resize", closeMenu);
      window.addEventListener("scroll", closeMenu, true);
    });

    onBeforeUnmount(() => {
      document.removeEventListener("mousedown", handleOutsideMouseDown);
      window.removeEventListener("resize", closeMenu);
      window.removeEventListener("scroll", closeMenu, true);
    });

    return () => {
      const node = nodeProps.node;
      const isMember = node.kind === "member";
      const hasChildren = Boolean(node.children?.length);
      const isOpen = nodeProps.keyword ? hasChildren : nodeProps.expanded.has(node.id);
      const isSelected = nodeProps.selected.has(node.id);
      const memberCount = isMember ? 0 : subtreeMemberCount(node, nodeProps.selected);

      const avatarColor = isMember ? node.avatarColor || colorFor(node.name) : colorFor(node.name);
      const avatarText = isMember ? node.avatarText || initials(node.name) : initials(node.name);

      const kebabIcon = h("svg", { viewBox: "0 0 16 16", "aria-hidden": "true" }, [
        h("circle", { cx: "3.5", cy: "8", r: "1.5", fill: "currentColor" }),
        h("circle", { cx: "8", cy: "8", r: "1.5", fill: "currentColor" }),
        h("circle", { cx: "12.5", cy: "8", r: "1.5", fill: "currentColor" }),
      ]);

      const trashIcon = h(
        "svg",
        { viewBox: "0 0 16 16", fill: "none", "aria-hidden": "true" },
        [
          h("path", { d: "M3 4.5H13", stroke: "currentColor", "stroke-width": 1.5, "stroke-linecap": "round" }),
          h("path", { d: "M6 4.5V3.2C6 2.8 6.3 2.5 6.7 2.5H9.3C9.7 2.5 10 2.8 10 3.2V4.5", stroke: "currentColor", "stroke-width": 1.5, "stroke-linecap": "round" }),
          h("path", { d: "M4.5 4.5L5 13C5 13.4 5.3 13.7 5.7 13.7H10.3C10.7 13.7 11 13.4 11 13L11.5 4.5", stroke: "currentColor", "stroke-width": 1.5, "stroke-linecap": "round", "stroke-linejoin": "round" }),
          h("path", { d: "M7 7V11M9 7V11", stroke: "currentColor", "stroke-width": 1.5, "stroke-linecap": "round" }),
        ]
      );

      const menuItems = [
        { key: "sub", label: "新建子组", danger: false },
        { key: "edit", label: "编辑", danger: false },
        { key: "delete", label: "删除", danger: true },
      ];

      const menu = [
        h(
          "button",
          {
            ref: actionButton,
            class: "gts-kebab",
            type: "button",
            "aria-label": "分组操作",
            "aria-expanded": popoverOpenId.value === node.id ? "true" : "false",
            onClick: (event) => toggleMenu(event, node),
          },
          [kebabIcon]
        ),
        popoverOpenId.value === node.id
          ? h(
              Teleport,
              { to: "body" },
              h(
                "div",
                {
                  ref: actionMenu,
                  class: "gts-menu-popover",
                  style: {
                    top: `${menuPosition.value.top}px`,
                    left: `${menuPosition.value.left}px`,
                  },
                  onClick: (event) => event.stopPropagation(),
                },
                h("div", { class: "gts-menu" }, [
                  ...menuItems.map((item, index) => [
                    index === menuItems.length - 1
                      ? h("div", { class: "gts-menu-sep" })
                      : null,
                    h(
                      "button",
                      {
                        key: item.key,
                        type: "button",
                        class: ["gts-menu-item", { "gts-menu-item--danger": item.danger }],
                        onClick: (event) => {
                          event.stopPropagation();
                          popoverOpenId.value = "";
                          if (item.key === "sub") nodeEmit("sub-create", node);
                          else if (item.key === "edit") nodeEmit("edit", node);
                          else if (item.key === "delete") nodeEmit("delete", node);
                        },
                      },
                      item.label
                    ),
                  ]).flat(),
                ])
              )
            )
          : null,
      ];

      // Member rows only show a trash button (no kebab menu).
      const trailing = isMember
        ? h(
            "span",
            {
              class: "gts-actions",
              onClick: (event) => event.stopPropagation(),
            },
            [
              h(
                "button",
                {
                  class: "gts-kebab gts-kebab--remove",
                  type: "button",
                  "aria-label": "移除成员",
                  onClick: (event) => {
                    event.stopPropagation();
                    nodeEmit("remove-member", node.userId);
                  },
                },
                [trashIcon]
              ),
            ]
          )
        : h(
            "span",
            {
              class: "gts-actions",
              onClick: (event) => event.stopPropagation(),
            },
            [menu]
          );

      return h("div", { class: ["gts-node", { "gts-node--member": isMember }] }, [
        h(
          "div",
          {
            class: [
              "gts-row",
              {
                "gts-row--selected": isSelected,
                "gts-row--disabled": !nodeProps.selectable,
                "gts-row--member": isMember,
              },
            ],
            style: { paddingLeft: `${nodeProps.depth * 18 + 8}px` },
            role: "treeitem",
            "aria-selected": nodeProps.selectable ? String(isSelected) : undefined,
            onClick: () => !isMember && nodeProps.selectable && nodeEmit("toggle-select", node),
          },
          [
            h(
              "button",
              {
                class: ["gts-chevron", { "gts-chevron--open": isOpen, "gts-chevron--hidden": !hasChildren }],
                type: "button",
                "aria-label": isOpen ? "收起" : "展开",
                onClick: (event) => {
                  event.stopPropagation();
                  if (hasChildren) nodeEmit("toggle-expand", node.id);
                },
              },
              hasChildren
                ? h("svg", { viewBox: "0 0 12 12", fill: "none", "aria-hidden": "true" }, [
                    h("path", {
                      d: "M4 2L8 6L4 10",
                      stroke: "currentColor",
                      "stroke-width": 1.6,
                      "stroke-linecap": "round",
                      "stroke-linejoin": "round",
                    }),
                  ])
                : null
            ),
            h(
              "span",
              {
                class: "gts-avatar",
                style: { background: avatarColor },
              },
              avatarText
            ),
            h("span", { class: "gts-name" }, highlightedName(node.name, nodeProps.keyword)),
            isMember && node.meta
              ? h("span", { class: "gts-member-meta" }, node.meta)
              : null,
            !isMember && memberCount ? h("span", { class: "gts-count" }, memberCount) : null,
            trailing,
          ]
        ),
        hasChildren && isOpen
          ? h(
              "div",
              { class: "gts-children", role: "group" },
              node.children.map((child) =>
                h(TreeNode, {
                  key: child.id,
                  node: child,
                  depth: nodeProps.depth + 1,
                  keyword: nodeProps.keyword,
                  multiple: nodeProps.multiple,
                  selectable: nodeProps.selectable,
                  selected: nodeProps.selected,
                  expanded: nodeProps.expanded,
                  onToggleExpand: (id) => nodeEmit("toggle-expand", id),
                  onToggleSelect: (selectedNode) => nodeEmit("toggle-select", selectedNode),
                  onSubCreate: (n) => nodeEmit("sub-create", n),
                  onEdit: (n) => nodeEmit("edit", n),
                  onDelete: (n) => nodeEmit("delete", n),
                  onRemoveMember: (userId) => nodeEmit("remove-member", userId),
                })
              )
            )
          : null,
      ]);
    };
  },
});
</script>

<style>
.group-tree-select {
  --gts-accent: #2563eb;
  --gts-accent-soft: #eff6ff;
  --gts-border: #d9dee8;
  --gts-text: #172033;
  --gts-muted: #697386;
  overflow: hidden;
  border: 1px solid var(--gts-border);
  border-radius: 8px;
  background: #ffffff;
}

.group-tree-select--compact .gts-tree {
  max-height: 250px;
}

.group-tree-select--compact .gts-row {
  min-height: 32px;
}

.gts-search {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--gts-border);
  background: #f8fafc;
  padding: 10px 12px;
}

.gts-search-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #8a94a6;
}

.gts-search-input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--gts-text);
  font-size: 13px;
}

.gts-search-input::placeholder {
  color: #98a2b3;
}

.gts-search-clear {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
}

.gts-search-clear:hover {
  background: #e9eef6;
  color: var(--gts-text);
}

.gts-search-clear svg {
  width: 12px;
  height: 12px;
}

.gts-tree {
  max-height: 430px;
  overflow: auto;
  padding: 6px;
}

.gts-empty {
  margin: 0;
  padding: 28px 10px;
  color: var(--gts-muted);
  font-size: 13px;
  text-align: center;
}

.gts-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  border-radius: 7px;
  padding-right: 8px;
  cursor: pointer;
  user-select: none;
}

.gts-row:hover {
  background: #f3f6fb;
}

.gts-row--selected,
.gts-row--selected:hover {
  background: var(--gts-accent-soft);
  color: var(--gts-accent);
}

.gts-row--disabled {
  cursor: default;
}

.gts-chevron {
  display: grid;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #8a94a6;
  cursor: pointer;
  transition: background 140ms ease, transform 140ms ease;
}

.gts-chevron:hover {
  background: #e9eef6;
  color: var(--gts-text);
}

.gts-chevron svg {
  width: 11px;
  height: 11px;
}

.gts-chevron--open {
  transform: rotate(90deg);
}

.gts-chevron--hidden {
  visibility: hidden;
}

.gts-checkbox,
.gts-radio {
  display: grid;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  place-items: center;
  border: 1.5px solid #c8d0dc;
  border-radius: 4px;
  background: #ffffff;
  transition: background 120ms ease, border-color 120ms ease;
}

.gts-radio {
  border-radius: 50%;
}

.gts-checkbox.is-checked {
  border-color: var(--gts-accent);
  background: var(--gts-accent);
}

.gts-checkbox svg {
  width: 10px;
  height: 10px;
}

.gts-radio.is-checked {
  border-color: var(--gts-accent);
}

.gts-radio.is-checked::after {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gts-accent);
  content: "";
}

.gts-avatar {
  display: grid;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 7px;
  color: #ffffff;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0;
}

.gts-name {
  min-width: 0;
  overflow: hidden;
  color: var(--gts-text);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gts-name mark {
  border-radius: 3px;
  background: #fef3c7;
  color: #92400e;
  padding: 0 1px;
}

.gts-count {
  margin-left: auto;
  flex-shrink: 0;
  border-radius: 999px;
  background: #eef2f7;
  color: var(--gts-muted);
  font-size: 11px;
  font-weight: 800;
  padding: 1px 7px;
}

.gts-actions {
  display: inline-flex;
  flex-shrink: 0;
  gap: 4px;
  margin-left: 4px;
  opacity: 0;
  transition: opacity 140ms ease;
}

.gts-row:hover .gts-actions,
.gts-row--selected .gts-actions {
  opacity: 1;
}

.gts-children {
  position: relative;
}

.gts-kebab {
  display: grid;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  place-items: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
  transition: background 140ms ease, color 140ms ease;
}

.gts-kebab:hover {
  background: #e9eef6;
  color: var(--gts-text);
}

.gts-kebab svg {
  width: 16px;
  height: 16px;
}

/* Dropdown menu. */
.gts-menu-popover {
  position: fixed;
  z-index: 60;
  width: 116px;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12), 0 2px 6px rgba(15, 23, 42, 0.06);
  padding: 4px;
}

.gts-menu {
  display: grid;
  gap: 2px;
}

.gts-menu-item {
  display: block;
  width: 100%;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--gts-text);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  padding: 7px 9px;
  text-align: left;
  transition: background 120ms ease, color 120ms ease;
}

.gts-menu-item:hover {
  background: #f4f7fb;
}

.gts-menu-item--danger {
  color: var(--gts-danger, #dc2626);
}

.gts-menu-item--danger:hover {
  background: #fef2f2;
}

.gts-menu-sep {
  height: 1px;
  margin: 3px 2px;
  background: #eef1f6;
}

/* Member nodes rendered as leaf children of the selected group. */
.gts-row--member {
  min-height: 34px;
  cursor: default;
}

.gts-row--member .gts-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 9px;
}

.gts-row--member .gts-name {
  color: var(--gts-muted);
  font-weight: 700;
}

.gts-member-meta {
  min-width: 0;
  margin-left: auto;
  overflow: hidden;
  flex-shrink: 1;
  color: #aab2c0;
  font-size: 11px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Member rows always show their trash action (no hover required). */
.gts-row--member .gts-actions {
  opacity: 1;
}

.gts-kebab--remove:hover {
  background: #fef2f2;
  color: var(--gts-danger, #dc2626);
}

.gts-kebab--remove svg {
  width: 15px;
  height: 15px;
}

@media (max-width: 760px) {
  .gts-actions {
    opacity: 1;
  }

  .gts-row {
    align-items: flex-start;
    min-height: 40px;
    padding-top: 5px;
    padding-bottom: 5px;
  }
}
</style>
