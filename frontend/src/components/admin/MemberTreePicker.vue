<template>
  <div class="member-tree-picker">
    <section class="mtp-tree-panel">
      <div class="mtp-search">
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <circle cx="9" cy="9" r="6.5" stroke="currentColor" stroke-width="1.6" />
          <path d="M14 14L18 18" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <input v-model="keyword" type="text" :placeholder="placeholder" />
        <button v-if="keyword" type="button" aria-label="清除搜索" @click="keyword = ''">
          <svg viewBox="0 0 14 14" fill="none" aria-hidden="true">
            <path d="M2 2L12 12M12 2L2 12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="mtp-tree" role="tree">
        <p v-if="!visibleTree.length" class="mtp-empty">{{ keyword ? "没有匹配的用户" : emptyText }}</p>
        <TreeNode
          v-for="node in visibleTree"
          :key="node.id"
          :node="node"
          :depth="0"
          :selected="selectedSet"
          :expanded="expandedKeys"
          :keyword="keyword"
          @toggle-expand="toggleExpand"
          @toggle-select="toggleSelect"
        />
      </div>
    </section>

    <section class="mtp-selected-panel">
      <header class="mtp-selected-head">已选用户 ({{ selectedUsers.length }})</header>
      <div v-if="selectedUsers.length" class="mtp-selected-list">
        <article v-for="user in selectedUsers" :key="user.id" class="mtp-selected-item">
          <span class="mtp-avatar" :style="{ background: colorFor(userLabel(user)) }">
            {{ initials(userLabel(user)) }}
          </span>
          <span class="mtp-selected-text">
            <strong>{{ userLabel(user) }}</strong>
            <small>{{ userMeta(user) }}</small>
          </span>
          <button type="button" aria-label="移除" @click="removeUser(user.id)">×</button>
        </article>
      </div>
      <div v-else class="mtp-selected-empty">
        <strong>暂未选择用户</strong>
        <span>在左侧勾选用户后会显示在这里。</span>
      </div>
      <footer class="mtp-selected-foot">
        <span>共 {{ selectedUsers.length }} 人</span>
        <button type="button" :disabled="!selectedUsers.length" @click="clearSelected">清空</button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref, watch } from "vue";

const props = defineProps({
  users: { type: Array, default: () => [] },
  modelValue: { type: Array, default: () => [] },
  // Org-tree mode: pass a department tree (OrgNode[] from the backend) instead of a flat users list.
  orgTree: { type: Array, default: () => [] },
  // In org mode: targetIds of users already in the group (to show checked state).
  selectedTargetIds: { type: Array, default: () => [] },
  rootLabel: { type: String, default: "已导入用户" },
  placeholder: { type: String, default: "搜索用户名称、手机号或邮箱" },
  emptyText: { type: String, default: "没有可选择的用户" },
});

const emit = defineEmits(["update:modelValue", "selection-change"]);
const keyword = ref("");
const ROOT_ID = "__root__";

// Org mode: build a unified tree from the orgTree prop.
const isOrgMode = computed(() => props.orgTree && props.orgTree.length > 0);

function mapOrgNode(node) {
  const isUser = !node.department;
  return {
    id: node.id,
    type: isUser ? "user" : "group",
    name: node.name || (isUser ? node.targetId : node.id),
    title: isUser ? node.targetId : "",
    targetId: isUser ? node.targetId : null,
    children: (node.children || []).map(mapOrgNode),
  };
}

const orgMappedTree = computed(() => {
  if (!isOrgMode.value) return [];
  // Wrap under a single root so expand-all-on-load works uniformly.
  return [
    {
      id: ROOT_ID,
      type: "group",
      name: props.rootLabel,
      children: props.orgTree.map(mapOrgNode),
    },
  ];
});

const expandedKeys = ref(new Set([ROOT_ID]));
watch(
  () => props.orgTree,
  () => {
    if (isOrgMode.value) {
      // Expand all department nodes by default so users are visible.
      const all = new Set([ROOT_ID]);
      const walk = (nodes) => nodes.forEach((n) => {
        if (n.department) all.add(n.id);
        walk(n.children || []);
      });
      props.orgTree.forEach(walk);
      expandedKeys.value = all;
    }
  },
  { immediate: true, deep: true }
);

// In org mode, track selected targetIds; in flat mode, selected user ids.
const selectedSet = computed(() => new Set(isOrgMode.value ? props.selectedTargetIds : props.modelValue));
const userMap = computed(() => new Map(props.users.map((user) => [user.id, user])));

// Build the node for getUserIds: in org mode we key by targetId for users.
const tree = computed(() => {
  if (isOrgMode.value) return orgMappedTree.value;
  if (!props.users.length) return [];
  return [
    {
      id: ROOT_ID,
      type: "group",
      name: props.rootLabel,
      children: props.users.map((user) => ({
        id: user.id,
        type: "user",
        name: userLabel(user),
        title: userMeta(user),
        user,
      })),
    },
  ];
});

// Selected display list: org mode uses org selections, flat mode uses user objects.
const orgSelections = ref(new Set()); // targetIds chosen via org picker (not yet committed to group)
const selectedUsers = computed(() => {
  if (isOrgMode.value) {
    return collectOrgUsers(orgMappedTree.value)
      .filter((n) => orgSelections.value.has(n.id))
      .map((n) => ({ name: n.name, targetId: n.targetId, id: n.id }));
  }
  return props.modelValue.map((id) => userMap.value.get(id)).filter(Boolean);
});

function collectOrgUsers(nodes) {
  const out = [];
  const walk = (list) => list.forEach((n) => {
    if (n.type === "user") out.push(n);
    walk(n.children || []);
  });
  walk(nodes);
  return out;
}

watch(
  () => props.users,
  () => {
    if (!isOrgMode.value) expandedKeys.value = new Set([ROOT_ID]);
  },
  { deep: true }
);

const visibleTree = computed(() => {
  const term = keyword.value.trim().toLowerCase();
  if (!term) return tree.value;

  return tree.value
    .map((root) => {
      const children = root.children.filter((node) =>
        [node.name, node.title, node.user?.targetId].some((value) => String(value || "").toLowerCase().includes(term))
      );
      return children.length ? { ...root, children } : null;
    })
    .filter(Boolean);
});

function getUserIds(node) {
  if (node.type === "user") return [node.id];
  return (node.children || []).flatMap(getUserIds);
}

function toggleSelect(node) {
  if (isOrgMode.value) {
    const ids = getUserIds(node);
    const next = new Set(orgSelections.value);
    const allSelected = ids.length > 0 && ids.every((id) => next.has(id));
    ids.forEach((id) => { allSelected ? next.delete(id) : next.add(id); });
    orgSelections.value = next;
    // Emit the {targetId, name} pairs for the newly selected set.
    const chosen = collectOrgUsers(orgMappedTree.value).filter((n) => next.has(n.id))
      .map((n) => ({ targetId: n.targetId, name: n.name }));
    emit("selection-change", chosen);
    return;
  }
  const ids = getUserIds(node);
  const next = new Set(props.modelValue);
  const allSelected = ids.length > 0 && ids.every((id) => next.has(id));
  ids.forEach((id) => {
    allSelected ? next.delete(id) : next.add(id);
  });
  updateSelected(Array.from(next));
}

function updateSelected(ids) {
  emit("update:modelValue", ids);
}

function removeUser(id) {
  if (isOrgMode.value) {
    const next = new Set(orgSelections.value);
    next.delete(id);
    orgSelections.value = next;
    const chosen = collectOrgUsers(orgMappedTree.value).filter((n) => next.has(n.id))
      .map((n) => ({ targetId: n.targetId, name: n.name }));
    emit("selection-change", chosen);
    return;
  }
  updateSelected(props.modelValue.filter((item) => item !== id));
}

function clearSelected() {
  if (isOrgMode.value) {
    orgSelections.value = new Set();
    emit("selection-change", []);
    return;
  }
  updateSelected([]);
}

function toggleExpand(id) {
  const next = new Set(expandedKeys.value);
  next.has(id) ? next.delete(id) : next.add(id);
  expandedKeys.value = next;
}

const palette = ["#2563eb", "#0f9f6e", "#d97706", "#dc2626", "#7c3aed", "#0891b2", "#be185d"];

function colorFor(name) {
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return palette[hash % palette.length];
}

function initials(name) {
  return name.trim().slice(0, 2).toUpperCase() || "用";
}

function userLabel(user) {
  return user?.name || user?.phone || user?.email || user?.targetId || "未命名用户";
}

function userMeta(user) {
  const parts = [user?.phone, user?.email].filter(Boolean);
  return parts.length ? parts.join(" · ") : user?.targetId || "无附加信息";
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
  name: "MemberTreeNode",
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, required: true },
    selected: { type: Set, required: true },
    expanded: { type: Set, required: true },
    keyword: { type: String, default: "" },
  },
  emits: ["toggle-expand", "toggle-select"],
  setup(nodeProps, { emit: nodeEmit }) {
    return () => {
      const node = nodeProps.node;
      const hasChildren = Boolean(node.children?.length);
      const isOpen = nodeProps.keyword ? hasChildren : nodeProps.expanded.has(node.id);
      const ids = getUserIds(node);
      const checkedCount = ids.filter((id) => nodeProps.selected.has(id)).length;
      const isChecked = ids.length > 0 && checkedCount === ids.length;
      const isIndeterminate = checkedCount > 0 && checkedCount < ids.length;
      const isUser = node.type === "user";

      return h("div", { class: "mtp-node" }, [
        h(
          "div",
          {
            class: ["mtp-row", { "mtp-row--checked": isChecked && isUser }],
            style: { paddingLeft: `${nodeProps.depth * 30 + 12}px` },
            role: "treeitem",
            onClick: () => nodeEmit("toggle-select", node),
          },
          [
            h(
              "button",
              {
                class: ["mtp-chevron", { "mtp-chevron--open": isOpen, "mtp-chevron--hidden": !hasChildren }],
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
                      "stroke-width": 1.8,
                      "stroke-linecap": "round",
                      "stroke-linejoin": "round",
                    }),
                  ])
                : null
            ),
            h(
              "span",
              { class: ["mtp-checkbox", { "is-checked": isChecked, "is-indeterminate": isIndeterminate }] },
              isChecked
                ? h("svg", { viewBox: "0 0 12 12", fill: "none", "aria-hidden": "true" }, [
                    h("path", {
                      d: "M2 6L5 9L10 3",
                      stroke: "#fff",
                      "stroke-width": 1.8,
                      "stroke-linecap": "round",
                      "stroke-linejoin": "round",
                    }),
                  ])
                : isIndeterminate
                  ? h("span")
                  : null
            ),
            isUser
              ? h("span", { class: "mtp-avatar", style: { background: colorFor(node.name) } }, initials(node.name))
              : h("span", { class: "mtp-folder" }, [
                  h("svg", { viewBox: "0 0 20 20", fill: "none", "aria-hidden": "true" }, [
                    h("path", {
                      d: "M3 6.2C3 5.5 3.5 5 4.2 5h3.5l1.5 1.6h6.6c.7 0 1.2.5 1.2 1.2v6c0 .7-.5 1.2-1.2 1.2H4.2C3.5 15 3 14.5 3 13.8V6.2z",
                      fill: "currentColor",
                    }),
                  ]),
                ]),
            h("span", { class: "mtp-node-text" }, [
              h("strong", highlightedName(node.name, nodeProps.keyword)),
              isUser && node.title ? h("small", node.title) : null,
            ]),
            !isUser ? h("span", { class: "mtp-count" }, ids.length) : null,
          ]
        ),
        hasChildren && isOpen
          ? h(
              "div",
              { class: "mtp-children", role: "group" },
              node.children.map((child) =>
                h(TreeNode, {
                  key: child.id,
                  node: child,
                  depth: nodeProps.depth + 1,
                  selected: nodeProps.selected,
                  expanded: nodeProps.expanded,
                  keyword: nodeProps.keyword,
                  onToggleExpand: (id) => nodeEmit("toggle-expand", id),
                  onToggleSelect: (selectedNode) => nodeEmit("toggle-select", selectedNode),
                })
              )
            )
          : null,
      ]);
    };
  },
});
</script>
