import React, { useState } from "react";
import { Dropdown, DropdownButton } from "react-bootstrap";
import type { ShareholderIdNameMap } from "../DataType";

interface Props {
  shareholderIdNameMap: ShareholderIdNameMap;
}

const PortfolioSection: React.FC<Props> = ({ shareholderIdNameMap }) => {
  const map = shareholderIdNameMap ?? {};
  // 選択中のIDをローカルステートで管理
  const [selectedId, setSelectedId] = useState<number>(0);

  // title用
  const title =
    selectedId && map[selectedId]
      ? `株主ID: ${selectedId} | 株主名: ${map[selectedId]}`
      : "株主選択";

  return (
    <div
      style={{
        flex: 5,
        borderRight: "1px solid #ccc",
        paddingRight: "16px",
      }}
    >
      <h2>ポートフォリオ</h2>
      <DropdownButton
        title={title}
        onSelect={(eventKey) => setSelectedId(eventKey ? Number(eventKey) : 0)}
      >
        {Object.entries(map)
          .filter(([, name]) => typeof name === "string")
          .map(([id, name]) => (
            <Dropdown.Item key={id} eventKey={id}>
              {id}: {String(name)}
            </Dropdown.Item>
          ))}
      </DropdownButton>
      {/* <div>
        <pre>{JSON.stringify(map, null, 2)}</pre>
      </div> */}
      
    </div>
  );
};

export default PortfolioSection;